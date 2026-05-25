package com.bb.bot.aiAgent.tools;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 校验 SplatoonRecordTool 的纯选择逻辑覆盖用户 5 个场景。 */
public class SplatoonRecordSelectorCheck {
    static int fail = 0;

    static void eq(String name, Object got, Object exp) {
        boolean ok = (got == null && exp == null) || (got != null && got.toString().equals(String.valueOf(exp)));
        System.out.println((ok ? "PASS " : "FAIL ") + name + " => " + got + (ok ? "" : " (expected " + exp + ")"));
        if (!ok) fail++;
    }

    public static void main(String[] args) {
        // 场景2: 真格 → open+challenge 两个 modeId
        eq("modeVsIds(真格)", SplatoonRecordTool.modeVsIds("真格"), "[VnNNb2RlLTUx, VnNNb2RlLTI=]");
        eq("modeVsIds(anarchy)", SplatoonRecordTool.modeVsIds("anarchy"), "[VnNNb2RlLTUx, VnNNb2RlLTI=]");
        eq("modeVsIds(占地)", SplatoonRecordTool.modeVsIds("占地"), "[VnNNb2RlLTE=]");
        eq("modeVsIds(x)", SplatoonRecordTool.modeVsIds("x"), "[VnNNb2RlLTM=]");
        eq("modeVsIds(null)=空", SplatoonRecordTool.modeVsIds(null).isEmpty(), "true");

        // 场景3: 日期+时间解析(支持今天/昨天/前天/具体日期)
        java.time.LocalDate today = LocalDateTime.now().toLocalDate();
        eq("parseDateTime(18:50)=今天", SplatoonRecordTool.parseDateTime("18:50"), LocalDateTime.of(today, LocalTime.of(18, 50)));
        eq("parseDateTime(今天大概18:50左右)", SplatoonRecordTool.parseDateTime("今天大概18:50左右"), LocalDateTime.of(today, LocalTime.of(18, 50)));
        eq("parseDateTime(昨天19:00)", SplatoonRecordTool.parseDateTime("昨天19:00"), LocalDateTime.of(today.minusDays(1), LocalTime.of(19, 0)));
        eq("parseDateTime(前天20点10)", SplatoonRecordTool.parseDateTime("前天20点10"), LocalDateTime.of(today.minusDays(2), LocalTime.of(20, 10)));
        eq("parseDateTime(2天前14:00)", SplatoonRecordTool.parseDateTime("2天前14:00"), LocalDateTime.of(today.minusDays(2), LocalTime.of(14, 0)));
        eq("parseDateTime(2026-05-24 18:50)", SplatoonRecordTool.parseDateTime("2026-05-24 18:50"), LocalDateTime.of(java.time.LocalDate.of(2026, 5, 24), LocalTime.of(18, 50)));
        eq("parseDateTime(空)", SplatoonRecordTool.parseDateTime("随便"), null);

        // 筛选: 胜负/通关
        eq("battleJudgement(胜)", SplatoonRecordTool.battleJudgement("胜"), "WIN");
        eq("battleJudgement(输了)", SplatoonRecordTool.battleJudgement("输了"), "LOSE");
        eq("battleJudgement(null)", SplatoonRecordTool.battleJudgement(null), null);
        eq("coopClear(成功)", SplatoonRecordTool.coopClear("成功"), Boolean.TRUE);
        eq("coopClear(失败)", SplatoonRecordTool.coopClear("失败"), Boolean.FALSE);
        eq("coopClear(null)", SplatoonRecordTool.coopClear(null), null);

        // 场景1: 最近第四场 → index 4 取第4个(已降序)
        List<String> recs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) recs.add("rec" + i); // rec1=最近
        eq("pickByIndex(第4场)", SplatoonRecordTool.pickByIndex(recs, 4), "rec4");
        eq("pickByIndex(第1场)", SplatoonRecordTool.pickByIndex(recs, 1), "rec1");
        eq("pickByIndex(越界)", SplatoonRecordTool.pickByIndex(recs, 99), null);

        // 场景3: 最接近今天 18:50 的一条
        LocalDateTime base = today.atStartOfDay();
        List<LocalDateTime> times = new ArrayList<>();
        times.add(base.withHour(20).withMinute(10)); // 距 18:50 = 80min
        times.add(base.withHour(18).withMinute(45)); // 距 = 5min  <-- 最近
        times.add(base.withHour(12).withMinute(0));  // 距 = 410min
        LocalDateTime best = SplatoonRecordTool.pickClosest(times, t -> t, LocalDateTime.of(today, LocalTime.of(18, 50)));
        eq("pickClosest(18:50)", best == null ? null : best.toLocalTime(), LocalTime.of(18, 45));

        System.out.println(fail == 0 ? "ALL PASS" : (fail + " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
