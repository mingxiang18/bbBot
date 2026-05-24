package com.bb.bot.aiAgent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.common.util.nso.SplatoonTokenManager;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopEnemyDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopWaveDetailService;
import com.bb.bot.handler.splatoon.BbSplatoonUserHandler;
import com.bb.bot.handler.splatoon.render.SplatoonHtmlRenderer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Splatoon3 战绩智能查询工具（自然语言入口）。把 {@code 对战/打工 记录/详情} 能力暴露给 AI:
 * 支持 序号(最近第N场)、模式(真格/占地/X..)、出boss过滤、约某时间、刷新(先上传再查最新),
 * 找到对应记录后把渲染图直接发回会话。账号按 caller 绑定的 NSO 账号限定,不会越权看别人。
 */
@Slf4j
@Component
public class SplatoonRecordTool {

    @Autowired private ISplatoonBattleRecordService battleRecordService;
    @Autowired private ISplatoonBattleUserDetailService battleUserDetailService;
    @Autowired private ISplatoonCoopRecordsService coopRecordService;
    @Autowired private ISplatoonCoopUserDetailService coopUserDetailService;
    @Autowired private ISplatoonCoopWaveDetailService coopWaveDetailService;
    @Autowired private ISplatoonCoopEnemyDetailService coopEnemyDetailService;
    @Autowired private SplatoonTokenManager splatoonTokenManager;
    @Autowired private SplatoonHtmlRenderer splatoonHtmlRenderer;
    @Autowired private BbSplatoonUserHandler bbSplatoonUserHandler;

    /* ===================== 列表 ===================== */

    @AiTool(
            name = "splatoon_record_list",
            description = "查询并发送 Splatoon3(喷喷)对战/打工【记录列表】图(一屏多场)。"
                    + "type: battle=对战, coop=打工。count: 场数,默认5(1-10)。"
                    + "mode(仅对战,可选): 真格/anarchy、占地/turf、x、活动/event、祭典/fest、私房/private;不传=全部。"
                    + "bossOnly(仅打工,可选): true=只看打出头目鲑鱼(boss)的场次。"
                    + "refresh(可选): true=先上传拉取最新再查(用户说『最新』时用)。"
                    + "图直接发到会话,你拿到 ok 后只需简短附一句,不要复述图片内容。"
    )
    public Map<String, Object> recordList(
            @AiToolParam(name = "type", description = "battle=对战 / coop=打工") String type,
            @AiToolParam(name = "count", description = "场数,默认5", required = false) Integer count,
            @AiToolParam(name = "mode", description = "对战模式过滤:真格/占地/x/活动/祭典/私房", required = false) String mode,
            @AiToolParam(name = "bossOnly", description = "打工:仅出boss场次", required = false) Boolean bossOnly,
            @AiToolParam(name = "refresh", description = "先上传最新再查", required = false) Boolean refresh) {
        Map<String, Object> r = new LinkedHashMap<>();
        AgentReplySink sink = requireImageSink(r);
        if (sink == null) {
            return r;
        }
        String userId = MemoryToolContext.getUserId();
        List<String> accountIds = accountIds(userId);
        boolean coop = isCoop(type);
        int n = clamp(count == null ? 5 : count, 1, 10);

        if (Boolean.TRUE.equals(refresh)) {
            doRefresh(userId, coop, r);
        }
        try {
            File image;
            if (coop) {
                List<SplatoonCoopRecord> recs = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                        .in(SplatoonCoopRecord::getUserId, accountIds)
                        .isNotNull(Boolean.TRUE.equals(bossOnly), SplatoonCoopRecord::getBossName)
                        .orderByDesc(SplatoonCoopRecord::getPlayedTime).last("limit " + n));
                if (recs.isEmpty()) {
                    return notFound(r, "没有符合条件的打工记录" + (Boolean.TRUE.equals(bossOnly) ? "(出boss)" : "") + ",可先『上传打工记录』");
                }
                List<SplatoonCoopUserDetail> details = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                        .in(SplatoonCoopUserDetail::getCoopId, idStrings(recs.stream().map(SplatoonCoopRecord::getId).collect(Collectors.toList()))));
                image = splatoonHtmlRenderer.renderCoopList(recs, details);
                r.put("count", recs.size());
            } else {
                List<String> modeIds = modeVsIds(mode);
                List<SplatoonBattleRecord> recs = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                        .in(SplatoonBattleRecord::getUserId, accountIds)
                        .in(!modeIds.isEmpty(), SplatoonBattleRecord::getVsModeId, modeIds)
                        .orderByDesc(SplatoonBattleRecord::getPlayedTime).last("limit " + n));
                if (recs.isEmpty()) {
                    return notFound(r, "没有符合条件的对战记录" + (modeIds.isEmpty() ? "" : "(" + mode + ")") + ",可先『上传对战记录』");
                }
                List<SplatoonBattleUserDetail> details = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                        .in(SplatoonBattleUserDetail::getBattleId, idStrings(recs.stream().map(SplatoonBattleRecord::getId).collect(Collectors.toList()))));
                image = splatoonHtmlRenderer.renderBattleList(recs, details);
                r.put("count", recs.size());
            }
            sink.sendImage(image);
            r.put("ok", true);
            r.put("note", (coop ? "打工" : "对战") + "记录列表图已发送");
            return r;
        } catch (Exception e) {
            log.warn("splatoon_record_list 失败 type={} mode={}", type, mode, e);
            return fail(r, e);
        }
    }

    /* ===================== 详情 ===================== */

    @AiTool(
            name = "splatoon_record_detail",
            description = "查询并发送 Splatoon3(喷喷)对战/打工【单场详情】图(全队/全wave)。"
                    + "type: battle=对战, coop=打工。"
                    + "index: 最近第几场(1=最近一场),与 mode/bossOnly 过滤后计数;默认1。"
                    + "mode(仅对战,可选): 真格/占地/x/活动/祭典/私房。bossOnly(仅打工,可选)。"
                    + "aroundTime(可选): 形如 18:50,表示找今天该时间点附近的那场(给了它就忽略 index)。"
                    + "refresh(可选): true=先上传最新再查。图直接发到会话,拿到 ok 简短附一句即可。"
    )
    public Map<String, Object> recordDetail(
            @AiToolParam(name = "type", description = "battle=对战 / coop=打工") String type,
            @AiToolParam(name = "index", description = "最近第几场,1=最近,默认1", required = false) Integer index,
            @AiToolParam(name = "mode", description = "对战模式过滤", required = false) String mode,
            @AiToolParam(name = "bossOnly", description = "打工:仅出boss场次", required = false) Boolean bossOnly,
            @AiToolParam(name = "aroundTime", description = "今天某时间点附近,如 18:50", required = false) String aroundTime,
            @AiToolParam(name = "refresh", description = "先上传最新再查", required = false) Boolean refresh) {
        Map<String, Object> r = new LinkedHashMap<>();
        AgentReplySink sink = requireImageSink(r);
        if (sink == null) {
            return r;
        }
        String userId = MemoryToolContext.getUserId();
        List<String> accountIds = accountIds(userId);
        boolean coop = isCoop(type);
        if (Boolean.TRUE.equals(refresh)) {
            doRefresh(userId, coop, r);
        }
        LocalTime target = parseTime(aroundTime);
        int idx = clamp(index == null ? 1 : index, 1, 200);

        try {
            File image;
            if (coop) {
                List<SplatoonCoopRecord> recs = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                        .in(SplatoonCoopRecord::getUserId, accountIds)
                        .isNotNull(Boolean.TRUE.equals(bossOnly), SplatoonCoopRecord::getBossName)
                        .orderByDesc(SplatoonCoopRecord::getPlayedTime).last("limit 200"));
                SplatoonCoopRecord rec = target != null
                        ? pickClosest(recs, SplatoonCoopRecord::getPlayedTime, target)
                        : pickByIndex(recs, idx);
                if (rec == null) {
                    return notFound(r, "没找到符合条件的打工记录" + selectorDesc(idx, target, bossOnly, null));
                }
                String cid = String.valueOf(rec.getId());
                List<SplatoonCoopUserDetail> ud = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>().eq(SplatoonCoopUserDetail::getCoopId, cid));
                List<SplatoonCoopWaveDetail> waves = coopWaveDetailService.list(new LambdaQueryWrapper<SplatoonCoopWaveDetail>()
                        .eq(SplatoonCoopWaveDetail::getCoopId, cid).orderByAsc(SplatoonCoopWaveDetail::getWaveNumber));
                List<SplatoonCoopEnemyDetail> enemies = coopEnemyDetailService.list(new LambdaQueryWrapper<SplatoonCoopEnemyDetail>().eq(SplatoonCoopEnemyDetail::getCoopId, cid));
                image = splatoonHtmlRenderer.renderCoopDetail(rec, ud, waves, enemies);
                r.put("recordId", rec.getId());
            } else {
                List<String> modeIds = modeVsIds(mode);
                List<SplatoonBattleRecord> recs = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                        .in(SplatoonBattleRecord::getUserId, accountIds)
                        .in(!modeIds.isEmpty(), SplatoonBattleRecord::getVsModeId, modeIds)
                        .orderByDesc(SplatoonBattleRecord::getPlayedTime).last("limit 200"));
                SplatoonBattleRecord rec = target != null
                        ? pickClosest(recs, SplatoonBattleRecord::getPlayedTime, target)
                        : pickByIndex(recs, idx);
                if (rec == null) {
                    return notFound(r, "没找到符合条件的对战记录" + selectorDesc(idx, target, null, mode));
                }
                List<SplatoonBattleUserDetail> ud = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                        .eq(SplatoonBattleUserDetail::getBattleId, String.valueOf(rec.getId())));
                image = splatoonHtmlRenderer.renderBattleDetail(rec, ud);
                r.put("recordId", rec.getId());
            }
            sink.sendImage(image);
            r.put("ok", true);
            r.put("note", (coop ? "打工" : "对战") + "详情图已发送");
            return r;
        } catch (Exception e) {
            log.warn("splatoon_record_detail 失败 type={} idx={} time={}", type, idx, aroundTime, e);
            return fail(r, e);
        }
    }

    /* ===================== 选择逻辑(纯,便于单测) ===================== */

    /** 模式关键词 → vsModeId 列表;空 = 不过滤。 */
    static List<String> modeVsIds(String mode) {
        if (StringUtils.isBlank(mode)) {
            return Collections.emptyList();
        }
        String k = mode.trim().toLowerCase();
        if (k.contains("真格") || k.contains("蛮颓") || k.contains("anarchy") || k.contains("bankara") || k.contains("挑战") || k.contains("开放")) {
            return java.util.Arrays.asList("VnNNb2RlLTUx", "VnNNb2RlLTI=");
        }
        if (k.contains("占地") || k.contains("涂地") || k.contains("turf") || k.contains("regular") || k.contains("普通")) {
            return Collections.singletonList("VnNNb2RlLTE=");
        }
        if (k.equals("x") || k.contains("x比赛") || k.contains("xmatch")) {
            return Collections.singletonList("VnNNb2RlLTM=");
        }
        if (k.contains("活动") || k.contains("event")) {
            return Collections.singletonList("VnNNb2RlLTQ=");
        }
        if (k.contains("祭典") || k.contains("fest")) {
            return java.util.Arrays.asList("VnNNb2RlLTY=", "VnNNb2RlLTg=");
        }
        if (k.contains("私") || k.contains("private")) {
            return Collections.singletonList("VnNNb2RlLTU=");
        }
        return Collections.emptyList();
    }

    /** "18:50" / "今天18点50" → LocalTime;无则 null。 */
    static LocalTime parseTime(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        Matcher m = Pattern.compile("(\\d{1,2})[:：点](\\d{1,2})").matcher(s);
        if (!m.find()) {
            Matcher h = Pattern.compile("(\\d{1,2})\\s*点").matcher(s);
            if (h.find()) {
                int hh = Integer.parseInt(h.group(1));
                return hh < 24 ? LocalTime.of(hh, 0) : null;
            }
            return null;
        }
        int hh = Integer.parseInt(m.group(1));
        int mm = Integer.parseInt(m.group(2));
        return (hh < 24 && mm < 60) ? LocalTime.of(hh, mm) : null;
    }

    /** 已按时间降序的列表里取第 index(1基) 个。 */
    static <T> T pickByIndex(List<T> orderedDesc, int index1) {
        int i = index1 - 1;
        return (i >= 0 && i < orderedDesc.size()) ? orderedDesc.get(i) : null;
    }

    /** 取 playedTime 最接近「今天某 time」的一条。 */
    static <T> T pickClosest(List<T> list, java.util.function.Function<T, LocalDateTime> timeGetter, LocalTime time) {
        LocalDateTime targetToday = LocalDateTime.of(LocalDateTime.now().toLocalDate(), time);
        T best = null;
        long bestDiff = Long.MAX_VALUE;
        for (T t : list) {
            LocalDateTime pt = timeGetter.apply(t);
            if (pt == null) {
                continue;
            }
            long diff = Math.abs(Duration.between(pt, targetToday).toMinutes());
            if (diff < bestDiff) {
                bestDiff = diff;
                best = t;
            }
        }
        return best;
    }

    /* ===================== 辅助 ===================== */

    private boolean isCoop(String type) {
        String t = type == null ? "" : type.trim().toLowerCase();
        return t.contains("coop") || t.contains("打工") || t.contains("salmon") || t.contains("鲑");
    }

    private List<String> accountIds(String userId) {
        return splatoonTokenManager.getDataUsers(userId).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());
    }

    private List<String> idStrings(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.toList());
    }

    private void doRefresh(String userId, boolean coop, Map<String, Object> r) {
        try {
            if (coop) {
                bbSplatoonUserHandler.syncCoopRecords(userId);
            } else {
                bbSplatoonUserHandler.syncBattleRecords(userId);
            }
            r.put("refreshed", true);
        } catch (Exception e) {
            log.warn("刷新上传战绩失败 userId={} coop={}", userId, coop, e);
            r.put("refreshWarn", "上传最新战绩失败,展示的是已有记录: " + e.getMessage());
        }
    }

    private AgentReplySink requireImageSink(Map<String, Object> r) {
        AgentReplySink sink = AgentReplyContext.get();
        if (sink == null) {
            r.put("error", "no_active_conversation");
            r.put("hint", "当前不在可回传的会话里,无法发送图片");
            return null;
        }
        if (!sink.imageSupported()) {
            r.put("error", "client_no_image_capability");
            r.put("hint", "对方客户端不支持接收图片");
            return null;
        }
        return sink;
    }

    private Map<String, Object> notFound(Map<String, Object> r, String msg) {
        r.put("error", "not_found");
        r.put("hint", msg);
        return r;
    }

    private Map<String, Object> fail(Map<String, Object> r, Exception e) {
        r.put("error", "query_failed");
        r.put("message", e.getMessage());
        return r;
    }

    private String selectorDesc(int idx, LocalTime time, Boolean bossOnly, String mode) {
        List<String> parts = new ArrayList<>();
        if (time != null) {
            parts.add("约 " + time);
        } else {
            parts.add("第" + idx + "场");
        }
        if (Boolean.TRUE.equals(bossOnly)) {
            parts.add("出boss");
        }
        if (StringUtils.isNotBlank(mode)) {
            parts.add(mode);
        }
        return "(" + String.join("/", parts) + ")";
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
