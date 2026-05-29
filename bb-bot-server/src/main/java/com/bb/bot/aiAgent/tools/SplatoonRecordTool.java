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
import com.bb.bot.handler.splatoon.RecordType;
import com.bb.bot.handler.splatoon.render.SplatoonHtmlRenderer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
                    + "mode(仅对战,可选): 真格/anarchy、占地/turf、x、活动/event、祭典/fest、私房/private。"
                    + "result(可选): 对战=胜/负(win/lose),打工=通关/失败(clear/fail)。"
                    + "bossOnly(仅打工,可选): true=只看打出头目鲑鱼(boss)的场次。"
                    + "scale(仅打工,可选): 金/银/铜(gold/silver/bronze)=只看出了该色鳞片的场次。"
                    + "refresh(可选): true=先上传拉取最新再查(用户说『最新』时用)。"
                    + "图直接发到会话,你拿到 ok 后只需简短附一句,不要复述图片内容。"
    )
    public Map<String, Object> recordList(
            @AiToolParam(name = "type", description = "battle=对战 / coop=打工") String type,
            @AiToolParam(name = "count", description = "场数,默认5", required = false) Integer count,
            @AiToolParam(name = "mode", description = "对战模式过滤:真格/占地/x/活动/祭典/私房", required = false) String mode,
            @AiToolParam(name = "result", description = "胜负过滤:对战 胜/负;打工 通关/失败", required = false) String result,
            @AiToolParam(name = "bossOnly", description = "打工:仅出boss场次", required = false) Boolean bossOnly,
            @AiToolParam(name = "scale", description = "打工:仅出了 金/银/铜 鳞片的场次", required = false) String scale,
            @AiToolParam(name = "refresh", description = "先上传最新再查", required = false) Boolean refresh) {
        Map<String, Object> r = new LinkedHashMap<>();
        AgentReplySink sink = requireImageSink(r);
        if (sink == null) {
            return r;
        }
        String userId = requireBoundUser(r);
        if (userId == null) {
            return r;
        }
        List<String> accountIds = accountIds(userId);
        RecordType recordType = recordType(type);
        boolean coop = recordType == RecordType.COOP;
        int n = clamp(count == null ? 5 : count, 1, 10);

        if (Boolean.TRUE.equals(refresh)) {
            doRefresh(userId, coop, r);
        }
        try {
            File image;
            if (coop) {
                Boolean clear = coopClear(result);
                String sc = scaleKey(scale);
                List<SplatoonCoopRecord> recs = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                        .in(SplatoonCoopRecord::getUserId, accountIds)
                        .isNotNull(Boolean.TRUE.equals(bossOnly), SplatoonCoopRecord::getBossName)
                        .eq(Boolean.TRUE.equals(clear), SplatoonCoopRecord::getResultWave, 0)
                        .gt(Boolean.FALSE.equals(clear), SplatoonCoopRecord::getResultWave, 0)
                        .gt("gold".equals(sc), SplatoonCoopRecord::getGoldScale, 0)
                        .gt("silver".equals(sc), SplatoonCoopRecord::getSilverScale, 0)
                        .gt("bronze".equals(sc), SplatoonCoopRecord::getBronzeScale, 0)
                        .orderByDesc(SplatoonCoopRecord::getPlayedTime).last("limit " + n));
                if (recs.isEmpty()) {
                    return notFound(r, "没有符合条件的打工记录" + coopDesc(bossOnly, clear, sc) + ",可先『上传打工记录』");
                }
                List<SplatoonCoopUserDetail> details = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                        .in(SplatoonCoopUserDetail::getCoopId, idStrings(recs.stream().map(SplatoonCoopRecord::getId).collect(Collectors.toList()))));
                image = splatoonHtmlRenderer.renderCoopList(recs, details);
                r.put("count", recs.size());
            } else {
                List<String> modeIds = modeVsIds(mode);
                String judgement = battleJudgement(result);
                List<SplatoonBattleRecord> recs = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                        .in(SplatoonBattleRecord::getUserId, accountIds)
                        .in(!modeIds.isEmpty(), SplatoonBattleRecord::getVsModeId, modeIds)
                        .eq(judgement != null, SplatoonBattleRecord::getJudgement, judgement)
                        .orderByDesc(SplatoonBattleRecord::getPlayedTime).last("limit " + n));
                if (recs.isEmpty()) {
                    return notFound(r, "没有符合条件的对战记录" + battleDesc(mode, judgement) + ",可先『上传对战记录』");
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
                    + "index: 最近第几场(1=最近一场),与 mode/result/bossOnly 过滤后计数;默认1。"
                    + "mode(仅对战,可选): 真格/占地/x/活动/祭典/私房。"
                    + "result(可选): 对战 胜/负;打工 通关/失败。bossOnly(仅打工,可选)。"
                    + "scale(仅打工,可选): 金/银/铜=只看出了该色鳞片的场次。"
                    + "time(可选): 时间点,可含日期,如 18:50 / 昨天19:00 / 前天20:10 / 2026-05-24 18:50;"
                    + "给了 time 就按最接近这个时刻找(忽略 index),不限当天。"
                    + "refresh(可选): true=先上传最新再查。图直接发到会话,拿到 ok 简短附一句即可。"
    )
    public Map<String, Object> recordDetail(
            @AiToolParam(name = "type", description = "battle=对战 / coop=打工") String type,
            @AiToolParam(name = "index", description = "最近第几场,1=最近,默认1", required = false) Integer index,
            @AiToolParam(name = "mode", description = "对战模式过滤", required = false) String mode,
            @AiToolParam(name = "result", description = "胜负过滤:对战 胜/负;打工 通关/失败", required = false) String result,
            @AiToolParam(name = "bossOnly", description = "打工:仅出boss场次", required = false) Boolean bossOnly,
            @AiToolParam(name = "scale", description = "打工:仅出了 金/银/铜 鳞片的场次", required = false) String scale,
            @AiToolParam(name = "time", description = "时间点(可含日期),如 18:50 / 昨天19:00 / 2026-05-24 18:50", required = false) String time,
            @AiToolParam(name = "refresh", description = "先上传最新再查", required = false) Boolean refresh) {
        Map<String, Object> r = new LinkedHashMap<>();
        AgentReplySink sink = requireImageSink(r);
        if (sink == null) {
            return r;
        }
        String userId = requireBoundUser(r);
        if (userId == null) {
            return r;
        }
        List<String> accountIds = accountIds(userId);
        RecordType recordType = recordType(type);
        boolean coop = recordType == RecordType.COOP;
        if (Boolean.TRUE.equals(refresh)) {
            doRefresh(userId, coop, r);
        }
        LocalDateTime target = parseDateTime(time);
        int idx = clamp(index == null ? 1 : index, 1, 200);

        try {
            File image;
            if (coop) {
                Boolean clear = coopClear(result);
                String sc = scaleKey(scale);
                List<SplatoonCoopRecord> recs = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                        .in(SplatoonCoopRecord::getUserId, accountIds)
                        .isNotNull(Boolean.TRUE.equals(bossOnly), SplatoonCoopRecord::getBossName)
                        .eq(Boolean.TRUE.equals(clear), SplatoonCoopRecord::getResultWave, 0)
                        .gt(Boolean.FALSE.equals(clear), SplatoonCoopRecord::getResultWave, 0)
                        .gt("gold".equals(sc), SplatoonCoopRecord::getGoldScale, 0)
                        .gt("silver".equals(sc), SplatoonCoopRecord::getSilverScale, 0)
                        .gt("bronze".equals(sc), SplatoonCoopRecord::getBronzeScale, 0)
                        .orderByDesc(SplatoonCoopRecord::getPlayedTime).last("limit 200"));
                SplatoonCoopRecord rec = target != null
                        ? pickClosest(recs, SplatoonCoopRecord::getPlayedTime, target)
                        : pickByIndex(recs, idx);
                if (rec == null) {
                    return notFound(r, "没找到符合条件的打工记录" + coopDesc(bossOnly, clear, sc));
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
                String judgement = battleJudgement(result);
                List<SplatoonBattleRecord> recs = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                        .in(SplatoonBattleRecord::getUserId, accountIds)
                        .in(!modeIds.isEmpty(), SplatoonBattleRecord::getVsModeId, modeIds)
                        .eq(judgement != null, SplatoonBattleRecord::getJudgement, judgement)
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
            log.warn("splatoon_record_detail 失败 type={} idx={} time={}", type, idx, time, e);
            return fail(r, e);
        }
    }

    /* ===================== 模式 ID 常量 ===================== */

    /** Splatoon3 对战模式 ID(GraphQL Base64 vsModeId),供 {@link #modeVsIds} 引用。 */
    static final class SplatoonModes {
        /** 涂地/普通比赛。 */
        static final String TURF = "VnNNb2RlLTE=";
        /** 真格(蛮颓挑战)。 */
        static final String ANARCHY_CHALLENGE = "VnNNb2RlLTI=";
        /** 真格(蛮颓开放)。 */
        static final String ANARCHY_OPEN = "VnNNb2RlLTUx";
        /** X 比赛。 */
        static final String X = "VnNNb2RlLTM=";
        /** 活动比赛。 */
        static final String EVENT = "VnNNb2RlLTQ=";
        /** 私房。 */
        static final String PRIVATE = "VnNNb2RlLTU=";
        /** 祭典(开放)。 */
        static final String FEST_OPEN = "VnNNb2RlLTY=";
        /** 祭典(挑战)。 */
        static final String FEST_CHALLENGE = "VnNNb2RlLTg=";

        private SplatoonModes() {
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
            return java.util.Arrays.asList(SplatoonModes.ANARCHY_OPEN, SplatoonModes.ANARCHY_CHALLENGE);
        }
        if (k.contains("占地") || k.contains("涂地") || k.contains("turf") || k.contains("regular") || k.contains("普通")) {
            return Collections.singletonList(SplatoonModes.TURF);
        }
        if (k.equals("x") || k.contains("x比赛") || k.contains("xmatch")) {
            return Collections.singletonList(SplatoonModes.X);
        }
        if (k.contains("活动") || k.contains("event")) {
            return Collections.singletonList(SplatoonModes.EVENT);
        }
        if (k.contains("祭典") || k.contains("fest")) {
            return java.util.Arrays.asList(SplatoonModes.FEST_OPEN, SplatoonModes.FEST_CHALLENGE);
        }
        if (k.contains("私") || k.contains("private")) {
            return Collections.singletonList(SplatoonModes.PRIVATE);
        }
        return Collections.emptyList();
    }

    /**
     * 在去前后空白并转小写后的关键词文本里,按表顺序找第一个命中(任一别名作为子串)的条目,返回其映射值;
     * 全不命中或入参空白则返回 null。用于把"若干别名 → 单一筛选值"的多分支收敛为查表。
     */
    private static <V> V matchKeyword(String raw, List<Map.Entry<V, List<String>>> table) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String k = raw.trim().toLowerCase();
        for (Map.Entry<V, List<String>> entry : table) {
            for (String alias : entry.getValue()) {
                if (k.contains(alias)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static final List<Map.Entry<String, List<String>>> JUDGEMENT_TABLE = List.of(
            Map.entry("WIN", List.of("win", "胜", "赢")),
            Map.entry("LOSE", List.of("lose", "负", "输", "败")));

    /** 对战胜负过滤 → judgement;无则 null(不过滤)。 */
    static String battleJudgement(String result) {
        return matchKeyword(result, JUDGEMENT_TABLE);
    }

    private static final List<Map.Entry<Boolean, List<String>>> COOP_CLEAR_TABLE = List.of(
            Map.entry(Boolean.TRUE, List.of("clear", "通关", "成功", "胜", "win", "全清")),
            Map.entry(Boolean.FALSE, List.of("fail", "失败", "输", "败", "没过", "没通")));

    /** 打工成败过滤 → TRUE=通关 / FALSE=失败 / null=不过滤。 */
    static Boolean coopClear(String result) {
        return matchKeyword(result, COOP_CLEAR_TABLE);
    }

    private static final List<Map.Entry<String, List<String>>> SCALE_TABLE = List.of(
            Map.entry("gold", List.of("金", "gold")),
            Map.entry("silver", List.of("银", "silver")),
            Map.entry("bronze", List.of("铜", "bronze")));

    /** 鳞片筛选关键词 → gold/silver/bronze;无则 null(不过滤)。出了该色鳞片(数量>0)。 */
    static String scaleKey(String scale) {
        return matchKeyword(scale, SCALE_TABLE);
    }

    /**
     * 解析「日期+时间」为目标时刻,支持:纯时间(18:50/18点50/18点)=今天;
     * 相对日(今天/昨天/前天/大前天/N天前);绝对日(YYYY-MM-DD、MM-DD、M月D日)。
     * 只给日期不给时间 → 当天 12:00。完全无法解析 → null。
     */
    static LocalDateTime parseDateTime(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        java.time.LocalDate date = parseRelativeOrAbsoluteDate(s);
        LocalTime time = parseTimeOnly(s);
        if (date == null && time == null) {
            return null;
        }
        if (date == null) {
            date = LocalDateTime.now().toLocalDate();
        }
        if (time == null) {
            time = LocalTime.NOON;
        }
        return LocalDateTime.of(date, time);
    }

    static LocalTime parseTimeOnly(String s) {
        Matcher m = Pattern.compile("(\\d{1,2})\\s*[:：点]\\s*(\\d{1,2})").matcher(s);
        if (m.find()) {
            int hh = Integer.parseInt(m.group(1)), mm = Integer.parseInt(m.group(2));
            return (hh < 24 && mm < 60) ? LocalTime.of(hh, mm) : null;
        }
        Matcher h = Pattern.compile("(\\d{1,2})\\s*点").matcher(s);
        if (h.find()) {
            int hh = Integer.parseInt(h.group(1));
            return hh < 24 ? LocalTime.of(hh, 0) : null;
        }
        return null;
    }

    static java.time.LocalDate parseRelativeOrAbsoluteDate(String s) {
        java.time.LocalDate today = LocalDateTime.now().toLocalDate();
        if (s.contains("大前天")) return today.minusDays(3);
        if (s.contains("前天")) return today.minusDays(2);
        if (s.contains("昨天") || s.contains("昨日")) return today.minusDays(1);
        if (s.contains("今天") || s.contains("今日")) return today;
        Matcher nAgo = Pattern.compile("(\\d+)\\s*天前").matcher(s);
        if (nAgo.find()) return today.minusDays(Integer.parseInt(nAgo.group(1)));
        Matcher ymd = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})").matcher(s);
        if (ymd.find()) {
            try { return java.time.LocalDate.of(Integer.parseInt(ymd.group(1)), Integer.parseInt(ymd.group(2)), Integer.parseInt(ymd.group(3))); } catch (Exception ignore) {}
        }
        Matcher md = Pattern.compile("(\\d{1,2})[-/月](\\d{1,2})[日号]?").matcher(s);
        if (md.find()) {
            try { return java.time.LocalDate.of(today.getYear(), Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2))); } catch (Exception ignore) {}
        }
        return null;
    }

    /** 已按时间降序的列表里取第 index(1基) 个。 */
    static <T> T pickByIndex(List<T> orderedDesc, int index1) {
        int i = index1 - 1;
        return (i >= 0 && i < orderedDesc.size()) ? orderedDesc.get(i) : null;
    }

    /** 取 playedTime 最接近目标时刻的一条(任意日期/时间,非限当天)。 */
    static <T> T pickClosest(List<T> list, java.util.function.Function<T, LocalDateTime> timeGetter, LocalDateTime target) {
        T best = null;
        long bestDiff = Long.MAX_VALUE;
        for (T t : list) {
            LocalDateTime pt = timeGetter.apply(t);
            if (pt == null) {
                continue;
            }
            long diff = Math.abs(Duration.between(pt, target).toMinutes());
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

    /** type 关键字 → 记录类型；命中打工别名(coop/打工/salmon/鲑)为 COOP，否则 BATTLE(与重构前默认对战分支等价)。 */
    RecordType recordType(String type) {
        return isCoop(type) ? RecordType.COOP : RecordType.BATTLE;
    }

    /**
     * 校验当前 caller 已绑定喷喷账号：未绑定时把 not_bound 错误写入 {@code r} 并返回 null，
     * 已绑定返回 userId。文案与重构前两方法内联分支逐一等价。
     */
    private String requireBoundUser(Map<String, Object> r) {
        String userId = MemoryToolContext.getUserId();
        if (!splatoonTokenManager.isBound(userId)) {
            r.put("error", "not_bound");
            r.put("hint", "该用户未绑定喷喷账号,无法查询战绩。请管理员先用「绑定喷喷账号」给TA绑定。");
            return null;
        }
        return userId;
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

    /** 把若干描述片段拼成 {@code (a/b/c)};空片段列表返回空串。统一三处选择/筛选描述的拼接口径。 */
    static String formatDesc(List<String> parts) {
        return parts.isEmpty() ? "" : "(" + String.join("/", parts) + ")";
    }

    private String selectorDesc(int idx, LocalDateTime time, Boolean bossOnly, String mode) {
        List<String> parts = new ArrayList<>();
        if (time != null) {
            parts.add("约 " + time.format(TF));
        } else {
            parts.add("第" + idx + "场");
        }
        if (Boolean.TRUE.equals(bossOnly)) {
            parts.add("出boss");
        }
        if (StringUtils.isNotBlank(mode)) {
            parts.add(mode);
        }
        return formatDesc(parts);
    }

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private String coopDesc(Boolean bossOnly, Boolean clear, String scaleKey) {
        List<String> p = new ArrayList<>();
        if (Boolean.TRUE.equals(bossOnly)) p.add("出boss");
        if (Boolean.TRUE.equals(clear)) p.add("通关");
        else if (Boolean.FALSE.equals(clear)) p.add("失败");
        if ("gold".equals(scaleKey)) p.add("出金鳞片");
        else if ("silver".equals(scaleKey)) p.add("出银鳞片");
        else if ("bronze".equals(scaleKey)) p.add("出铜鳞片");
        return formatDesc(p);
    }

    private String battleDesc(String mode, String judgement) {
        List<String> p = new ArrayList<>();
        if (StringUtils.isNotBlank(mode)) p.add(mode);
        if ("WIN".equals(judgement)) p.add("胜");
        else if ("LOSE".equals(judgement)) p.add("负");
        return formatDesc(p);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
