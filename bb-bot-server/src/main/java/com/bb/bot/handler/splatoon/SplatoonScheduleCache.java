package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.handler.splatoon.render.ScheduleMapRenderer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 斯普拉遁对战 / 打工「单时段日程图」缓存。
 *
 * <p>定时（{@code SplatoonScheduleCacheSchedule}，固定 6 小时 + 启动预热）从 splatoon3.ink 拉一次
 * schedules.json，把每个时段的图预渲染落盘到 NFS（{@code /bot/static/splatoon/schedule/}），内存里只存
 * 「时段时间 → 落盘路径」的有序映射。一次拉取对战覆盖未来 24h（12 段 × 2h）、打工覆盖约 8 天（5 段），
 * 所以 6h 拉一次缓存窗口里永远含「当前正在进行」的时段。</p>
 *
 * <p>读取时按<strong>当前真实时间</strong>在列表里定位 base（{@code startTime<=now<endTime} 那个 = 当前），
 * 命令 {@code 图/下图/下下图…} 即 base+timeIndex。命中落盘图直接秒回；
 * 拿不到（超出预渲染范围、或启动预热没跑完 / 上次拉取失败）则实时拉接口渲染该时段兜底。</p>
 *
 * <p>{@code 全图 / 全工}（timeIndex == -1）不走这里，始终实时查询，见
 * {@link SplatoonScheduleService}。</p>
 */
@Slf4j
@Component
public class SplatoonScheduleCache {

    private static final String SCHEDULES_URL = "https://splatoon3.ink/data/schedules.json";

    /** 落盘目录（相对 file.path，即 /bot/static/ 下）。 */
    private static final String SCHEDULE_DIR = "splatoon/schedule/";

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private ScheduleMapRenderer scheduleMapRenderer;

    /** 缓存的对战单时段，按时间升序，[0] 为拉取时刻的当前时段。volatile：刷新时整体替换。 */
    private volatile List<CachedSlot> regularSlots = Collections.emptyList();

    /** 缓存的打工单时段，含义同上（每段图为「该段 + 下一段」拼接，与命令 {@code 工} 行为一致）。 */
    private volatile List<CachedSlot> coopSlots = Collections.emptyList();

    /** 一个已预渲染的时段：UTC 起止时间 + 落盘图绝对路径。包级可见以便单测构造。 */
    @Data
    @AllArgsConstructor
    static class CachedSlot {
        private LocalDateTime start;
        private LocalDateTime end;
        private String filePath;
    }

    /** 拉 schedules.json 的 data 节点。供刷新、实时兜底、活动海报复用。 */
    public JSONObject fetchSchedules() {
        return restUtils.get(SCHEDULES_URL, jsonHeaders(), JSONObject.class).getJSONObject("data");
    }

    /**
     * 重新拉取并预渲染全部单时段，整体替换缓存，最后清掉已不在窗口内的旧落盘图。
     * 由定时任务（6h）和启动预热调用；{@code synchronized} 防并发刷新互相覆盖。
     */
    public synchronized void refresh() {
        JSONObject data = fetchSchedules();
        this.regularSlots = buildRegularSlots(data);
        this.coopSlots = buildCoopSlots(data);
        cleanStaleFiles();
        log.info("斯普拉遁日程缓存刷新完成：对战 {} 段，打工 {} 段", regularSlots.size(), coopSlots.size());
    }

    /** 对战单时段（timeIndex>=0）。命中缓存直接返回，否则实时渲染兜底。 */
    public File getRegularMap(int timeIndex) {
        File cached = lookup(regularSlots, timeIndex);
        if (cached != null) {
            return cached;
        }
        log.info("对战日程缓存未命中 timeIndex={}，实时拉取兜底", timeIndex);
        return scheduleMapRenderer.writeRegularMap(fetchSchedules(), timeIndex);
    }

    /** 打工单时段（timeIndex>=0）。命中缓存直接返回，否则实时渲染兜底。 */
    public File getCoopMap(int timeIndex) {
        File cached = lookup(coopSlots, timeIndex);
        if (cached != null) {
            return cached;
        }
        log.info("打工日程缓存未命中 timeIndex={}，实时拉取兜底", timeIndex);
        return scheduleMapRenderer.writeCoopMap(fetchSchedules(), timeIndex);
    }

    /**
     * 按当前真实时间在缓存里定位 base（第一个尚未结束的时段 = 当前），返回 base+timeIndex 的落盘图。
     * 定位不到当前、目标越界、或落盘文件已不在则返回 null（交由调用方实时兜底）。
     */
    private File lookup(List<CachedSlot> slots, int timeIndex) {
        int target = resolveTargetIndex(slots, timeIndex, LocalDateTime.now(ZoneOffset.UTC));
        if (target < 0) {
            return null;
        }
        File file = new File(slots.get(target).getFilePath());
        return file.exists() ? file : null;
    }

    /**
     * 纯定位逻辑（无 IO、无 now() 副作用，便于单测）：在有序时段列表里按 now 找 base
     * （第一个 endTime 在 now 之后的时段 = 当前），返回 base+timeIndex 的下标。
     * 列表空、now 已过全部时段、或目标越界 → 返回 -1。
     */
    static int resolveTargetIndex(List<CachedSlot> slots, int timeIndex, LocalDateTime now) {
        if (slots == null || slots.isEmpty()) {
            return -1;
        }
        int base = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).getEnd().isAfter(now)) {
                base = i;
                break;
            }
        }
        if (base < 0) {
            return -1;
        }
        int target = base + timeIndex;
        if (target < 0 || target >= slots.size()) {
            return -1;
        }
        return target;
    }

    /** 预渲染对战 12 段：每段调用现有 {@link ScheduleMapRenderer#writeRegularMap} 出综合图，落盘按 startTime 命名。 */
    private List<CachedSlot> buildRegularSlots(JSONObject data) {
        JSONArray nodes = data.getJSONObject("regularSchedules").getJSONArray("nodes");
        List<CachedSlot> list = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            int index = i;
            CachedSlot slot = renderSlot(node, "regular_", () -> scheduleMapRenderer.writeRegularMap(data, index));
            if (slot != null) {
                list.add(slot);
            }
        }
        return list;
    }

    /** 预渲染打工各段：每段图为「该段 + 下一段」拼接，与命令 {@code 工} 行为一致。 */
    private List<CachedSlot> buildCoopSlots(JSONObject data) {
        JSONArray nodes = data.getJSONObject("coopGroupingSchedule").getJSONObject("regularSchedules").getJSONArray("nodes");
        List<CachedSlot> list = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            int index = i;
            CachedSlot slot = renderSlot(node, "coop_", () -> scheduleMapRenderer.writeCoopMap(data, index));
            if (slot != null) {
                list.add(slot);
            }
        }
        return list;
    }

    /**
     * 渲染单个时段并落盘：落盘图按 startTime 命名，每次刷新都<strong>重渲覆盖</strong>。
     *
     * <p>不能「已存在就跳过复用」：打工图 {@code writeCoopMap(data, i)} 画的是「第 i 段 + 第 i+1 段」，
     * 内容依赖<em>下一段</em>，并非自己这一段的纯函数。每个新打工段首次出现都在窗口末尾（无下一段→只画一张），
     * 若 skip 复用，等它滑成当前段时就会一直显示那张陈旧的单张图（`工` 本应是当前+下一段两张）。
     * 故一律重渲；重渲很便宜——stage/weapon 远程图由 {@code SplatoonImageFetcher} 落地缓存，不会重复下载。</p>
     *
     * <p>单段渲染失败（如祭典期对战节点结构差异）只记录并返回 null，该段不进缓存、读取时走实时兜底。</p>
     */
    private CachedSlot renderSlot(JSONObject node, String prefix, java.util.function.Supplier<File> render) {
        String startTime = node.getString("startTime");
        String endTime = node.getString("endTime");
        try {
            String targetPath = FileUtils.getAbsolutePath(SCHEDULE_DIR + prefix + keyOf(startTime) + ".png");
            File target = new File(targetPath);
            File rendered = render.get();
            if (target.getParentFile() != null && !target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }
            FileUtils.copyFileUsingFileStreams(rendered, target);
            return new CachedSlot(parseUtc(startTime), parseUtc(endTime), targetPath);
        } catch (Exception e) {
            log.error("预渲染日程时段失败 prefix={} start={}", prefix, startTime, e);
            return null;
        }
    }

    /** 删除落盘目录里不属于本轮窗口的旧图（时段滑走后的过期图）。 */
    private void cleanStaleFiles() {
        File dir = new File(FileUtils.getAbsolutePath(SCHEDULE_DIR));
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Set<String> keep = new HashSet<>();
        for (CachedSlot slot : regularSlots) {
            keep.add(slot.getFilePath());
        }
        for (CachedSlot slot : coopSlots) {
            keep.add(slot.getFilePath());
        }
        for (File file : files) {
            if (file.isFile() && !keep.contains(file.getPath())) {
                if (file.delete()) {
                    log.info("清理过期日程图：{}", file.getName());
                }
            }
        }
    }

    /** startTime 转安全文件名（冒号不能做文件名），如 2026-05-30T06:00:00Z → 2026-05-30T06-00-00Z。 */
    static String keyOf(String startTime) {
        return startTime.replace(':', '-');
    }

    /** UTC 字符串（...Z）按 UTC 墙钟时间解析，便于与 now(UTC) 直接比较。 */
    static LocalDateTime parseUtc(String time) {
        return LocalDateTime.parse(time, DateUtils.timePattern);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        return headers;
    }
}
