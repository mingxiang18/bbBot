package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.handler.splatoon.SplatoonScheduleCache.CachedSlot;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SplatoonScheduleCache} 纯单元测试（零网络、零 Spring）。
 *
 * <p>重点覆盖整套缓存的「正确性核心」——按当前真实时间在有序时段列表里定位 base，
 * 因为 6 小时拉一次期间数组头部会随时间滑动过期，定位错了用户就会拿到旧时段图。
 * 另外覆盖文件名生成、UTC 解析、缓存命中直接返回、未命中实时兜底。</p>
 */
class SplatoonScheduleCacheTest {

    /** 基准时间：用相对它的偏移构造时段，避免依赖 now()。 */
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 5, 30, 6, 0, 0);

    /** 构造对战风格的连续 2 小时时段列表：[T0,T0+2h], [T0+2h,T0+4h], ... 共 count 段。 */
    private static List<CachedSlot> twoHourSlots(int count) {
        List<CachedSlot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(new CachedSlot(T0.plusHours(2L * i), T0.plusHours(2L * (i + 1)), "slot_" + i + ".png"));
        }
        return slots;
    }

    // ---------- resolveTargetIndex：按 now 定位 base（最关键） ----------

    @Test
    void resolveTargetIndex_nowInFirstSlot_baseIsZero() {
        List<CachedSlot> slots = twoHourSlots(12);
        LocalDateTime now = T0.plusMinutes(30); // 落在第 0 段

        assertEquals(0, SplatoonScheduleCache.resolveTargetIndex(slots, 0, now), "图=当前=第0段");
        assertEquals(1, SplatoonScheduleCache.resolveTargetIndex(slots, 1, now), "下图=第1段");
        assertEquals(2, SplatoonScheduleCache.resolveTargetIndex(slots, 2, now), "下下图=第2段");
    }

    @Test
    void resolveTargetIndex_sixHoursLater_baseSlidesForward() {
        // 模拟「拉取后过了 6 小时」：当前真实时间落在第 3 段（6h=3×2h），缓存数组头部已过期。
        List<CachedSlot> slots = twoHourSlots(12);
        LocalDateTime now = T0.plusHours(6).plusMinutes(15); // 落在第 3 段

        assertEquals(3, SplatoonScheduleCache.resolveTargetIndex(slots, 0, now), "图必须是当前(第3段)，不能是过期的第0段");
        assertEquals(4, SplatoonScheduleCache.resolveTargetIndex(slots, 1, now), "下图=第4段");
        assertEquals(5, SplatoonScheduleCache.resolveTargetIndex(slots, 2, now), "下下图=第5段");
    }

    @Test
    void resolveTargetIndex_nowExactlyAtSlotBoundary_picksNextNotYetEnded() {
        // now 正好等于第 0 段 endTime：第 0 段已结束（end.isAfter(now)=false），应进第 1 段。
        List<CachedSlot> slots = twoHourSlots(12);
        LocalDateTime now = T0.plusHours(2);

        assertEquals(1, SplatoonScheduleCache.resolveTargetIndex(slots, 0, now));
    }

    @Test
    void resolveTargetIndex_targetBeyondEnd_returnsMinusOne() {
        // 当前在第 10 段，下下图(+2)=第 12 段越界（只有 12 段 0..11）。
        List<CachedSlot> slots = twoHourSlots(12);
        LocalDateTime now = T0.plusHours(21); // 第 10 段 [20h,22h]

        assertEquals(10, SplatoonScheduleCache.resolveTargetIndex(slots, 0, now));
        assertEquals(11, SplatoonScheduleCache.resolveTargetIndex(slots, 1, now));
        assertEquals(-1, SplatoonScheduleCache.resolveTargetIndex(slots, 2, now), "越界返回 -1 → 走实时兜底");
    }

    @Test
    void resolveTargetIndex_nowAfterAllSlots_returnsMinusOne() {
        List<CachedSlot> slots = twoHourSlots(12);
        LocalDateTime now = T0.plusHours(100); // 远超全部时段

        assertEquals(-1, SplatoonScheduleCache.resolveTargetIndex(slots, 0, now));
    }

    @Test
    void resolveTargetIndex_emptyOrNull_returnsMinusOne() {
        assertEquals(-1, SplatoonScheduleCache.resolveTargetIndex(Collections.emptyList(), 0, T0));
        assertEquals(-1, SplatoonScheduleCache.resolveTargetIndex(null, 0, T0));
    }

    // ---------- keyOf / parseUtc ----------

    @Test
    void keyOf_replacesColonsForSafeFileName() {
        assertEquals("2026-05-30T06-00-00Z", SplatoonScheduleCache.keyOf("2026-05-30T06:00:00Z"));
    }

    @Test
    void parseUtc_parsesZuluString() {
        assertEquals(LocalDateTime.of(2026, 5, 30, 8, 0, 0),
                SplatoonScheduleCache.parseUtc("2026-05-30T08:00:00Z"));
    }

    // ---------- 缓存命中 / 未命中兜底（mock 依赖） ----------

    @Test
    void getRegularMap_cacheHit_returnsFileWithoutNetworkOrRender() throws Exception {
        RestUtils restUtils = mock(RestUtils.class);
        ScheduleMapRenderer renderer = mock(ScheduleMapRenderer.class);
        SplatoonScheduleCache cache = newCache(restUtils, renderer);

        // 当前(now)落在唯一一段内，落盘文件真实存在
        File real = File.createTempFile("regular_hit", ".png");
        real.deleteOnExit();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<CachedSlot> slots = Collections.singletonList(
                new CachedSlot(now.minusHours(1), now.plusHours(1), real.getPath()));
        ReflectionTestUtils.setField(cache, "regularSlots", slots);

        File got = cache.getRegularMap(0);

        assertSame(real.getPath(), got.getPath());
        verifyNoInteractions(restUtils);
        verify(renderer, never()).writeRegularMap(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void getRegularMap_cacheMiss_fallsBackToRealtimeRender() {
        RestUtils restUtils = mock(RestUtils.class);
        ScheduleMapRenderer renderer = mock(ScheduleMapRenderer.class);
        SplatoonScheduleCache cache = newCache(restUtils, renderer);
        // regularSlots 默认空 → 必然未命中

        JSONObject outer = new JSONObject();
        outer.put("data", new JSONObject());
        when(restUtils.get(anyString(), any(), eq(JSONObject.class))).thenReturn(outer);
        File fallback = new File("/tmp/fallback_regular.png");
        when(renderer.writeRegularMap(any(JSONObject.class), eq(2))).thenReturn(fallback);

        File got = cache.getRegularMap(2);

        assertSame(fallback, got, "未命中应返回实时渲染结果");
        verify(renderer).writeRegularMap(any(JSONObject.class), eq(2));
    }

    @Test
    void getRegularMap_cachedFileDeleted_fallsBackToRealtime() {
        RestUtils restUtils = mock(RestUtils.class);
        ScheduleMapRenderer renderer = mock(ScheduleMapRenderer.class);
        SplatoonScheduleCache cache = newCache(restUtils, renderer);

        // 时段定位命中，但落盘文件不存在（被清理）→ 兜底
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<CachedSlot> slots = Collections.singletonList(
                new CachedSlot(now.minusHours(1), now.plusHours(1), "/tmp/does-not-exist-xyz.png"));
        ReflectionTestUtils.setField(cache, "regularSlots", slots);

        JSONObject outer = new JSONObject();
        outer.put("data", new JSONObject());
        when(restUtils.get(anyString(), any(), eq(JSONObject.class))).thenReturn(outer);
        File fallback = new File("/tmp/fallback2.png");
        when(renderer.writeRegularMap(any(JSONObject.class), eq(0))).thenReturn(fallback);

        File got = cache.getRegularMap(0);

        assertSame(fallback, got);
        verify(renderer).writeRegularMap(any(JSONObject.class), eq(0));
    }

    @Test
    void getCoopMap_cacheHit_returnsFileWithoutNetworkOrRender() throws Exception {
        RestUtils restUtils = mock(RestUtils.class);
        ScheduleMapRenderer renderer = mock(ScheduleMapRenderer.class);
        SplatoonScheduleCache cache = newCache(restUtils, renderer);

        File real = File.createTempFile("coop_hit", ".png");
        real.deleteOnExit();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        List<CachedSlot> slots = Collections.singletonList(
                new CachedSlot(now.minusHours(10), now.plusHours(10), real.getPath()));
        ReflectionTestUtils.setField(cache, "coopSlots", slots);

        File got = cache.getCoopMap(0);

        assertSame(real.getPath(), got.getPath());
        verifyNoInteractions(restUtils);
        verify(renderer, never()).writeCoopMap(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static SplatoonScheduleCache newCache(RestUtils restUtils, ScheduleMapRenderer renderer) {
        SplatoonScheduleCache cache = new SplatoonScheduleCache();
        ReflectionTestUtils.setField(cache, "restUtils", restUtils);
        ReflectionTestUtils.setField(cache, "scheduleMapRenderer", renderer);
        return cache;
    }
}
