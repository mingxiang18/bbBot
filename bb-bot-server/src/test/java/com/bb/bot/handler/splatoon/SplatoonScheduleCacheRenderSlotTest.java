package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.SpringUtils;
import com.bb.bot.config.FilePathConfig;
import com.bb.bot.handler.splatoon.SplatoonScheduleCache.CachedSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：{@code SplatoonScheduleCache.renderSlot} 必须「即使落盘文件已存在也重渲覆盖」。
 *
 * <p>背景：打工 `工` 图是「当前段 + 下一段」拼接，内容依赖下一段。曾经按 startTime 命名 + 已存在就
 * 跳过复用，导致每个打工段首次出现在窗口末尾（无下一段→单张）时落了单张文件，等它滑成当前段后被复用，
 * `工` 就只剩一张。这里固定住「必须覆盖」的行为，防止再退化成单张。</p>
 */
class SplatoonScheduleCacheRenderSlotTest {

    private Path tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        tempRoot = Files.createTempDirectory("bb-renderslot-");
        FilePathConfig pathConfig = new FilePathConfig();
        ReflectionTestUtils.setField(pathConfig, "filePath", tempRoot.toAbsolutePath() + "/");
        // FileUtils.getAbsolutePath 通过 SpringUtils 拿 FilePathConfig
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        Mockito.when(beanFactory.getBean(FilePathConfig.class)).thenReturn(pathConfig);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", beanFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempRoot != null && Files.exists(tempRoot)) {
            try (Stream<Path> walk = Files.walk(tempRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void renderSlot_targetAlreadyExists_isOverwrittenNotSkipped() throws Exception {
        SplatoonScheduleCache cache = new SplatoonScheduleCache();

        JSONObject node = new JSONObject();
        node.put("startTime", "2026-05-28T16:00:00Z");
        node.put("endTime", "2026-05-30T08:00:00Z");

        // 预置一份「陈旧的单张图」落盘文件，模拟该段当初在窗口末尾时落下的单张
        Path scheduleDir = tempRoot.resolve("splatoon/schedule");
        Files.createDirectories(scheduleDir);
        Path target = scheduleDir.resolve("coop_2026-05-28T16-00-00Z.png");
        Files.writeString(target, "OLD-SINGLE-PANEL");

        // 本轮渲染产出「新的两张拼接图」内容
        File freshRender = File.createTempFile("fresh", ".png");
        freshRender.deleteOnExit();
        Files.writeString(freshRender.toPath(), "NEW-TWO-PANEL");
        Supplier<File> render = () -> freshRender;

        CachedSlot slot = ReflectionTestUtils.invokeMethod(cache, "renderSlot", node, "coop_", render);

        assertNotNull(slot, "应返回 CachedSlot");
        assertEquals(target.toAbsolutePath().toString(), slot.getFilePath(), "落盘路径按 startTime 命名");
        assertEquals("NEW-TWO-PANEL", Files.readString(target),
                "已存在的落盘图必须被本轮重渲覆盖（旧逻辑会跳过复用，残留单张）");
    }

    @Test
    void renderSlot_targetMissing_rendersFresh() throws Exception {
        SplatoonScheduleCache cache = new SplatoonScheduleCache();

        JSONObject node = new JSONObject();
        node.put("startTime", "2026-05-30T08:00:00Z");
        node.put("endTime", "2026-06-01T00:00:00Z");

        File freshRender = File.createTempFile("fresh2", ".png");
        freshRender.deleteOnExit();
        Files.writeString(freshRender.toPath(), "RENDERED");
        Supplier<File> render = () -> freshRender;

        CachedSlot slot = ReflectionTestUtils.invokeMethod(cache, "renderSlot", node, "coop_", render);

        assertNotNull(slot);
        Path expected = tempRoot.resolve("splatoon/schedule/coop_2026-05-30T08-00-00Z.png");
        assertTrue(Files.exists(expected), "目标不存在时应渲染落盘");
        assertEquals("RENDERED", Files.readString(expected));
    }
}
