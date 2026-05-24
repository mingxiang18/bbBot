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

        // 场景3: 时间解析
        eq("parseTime(18:50)", SplatoonRecordTool.parseTime("18:50"), LocalTime.of(18, 50));
        eq("parseTime(今天大概18:50左右)", SplatoonRecordTool.parseTime("今天大概18:50左右"), LocalTime.of(18, 50));
        eq("parseTime(18点50)", SplatoonRecordTool.parseTime("18点50"), LocalTime.of(18, 50));
        eq("parseTime(18点)", SplatoonRecordTool.parseTime("18点"), LocalTime.of(18, 0));
        eq("parseTime(空)", SplatoonRecordTool.parseTime("随便"), null);

        // 场景1: 最近第四场 → index 4 取第4个(已降序)
        List<String> recs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) recs.add("rec" + i); // rec1=最近
        eq("pickByIndex(第4场)", SplatoonRecordTool.pickByIndex(recs, 4), "rec4");
        eq("pickByIndex(第1场)", SplatoonRecordTool.pickByIndex(recs, 1), "rec1");
        eq("pickByIndex(越界)", SplatoonRecordTool.pickByIndex(recs, 99), null);

        // 场景3: 最接近今天 18:50 的一条
        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<LocalDateTime> times = new ArrayList<>();
        times.add(today.withHour(20).withMinute(10)); // 距 18:50 = 80min
        times.add(today.withHour(18).withMinute(45)); // 距 = 5min  <-- 最近
        times.add(today.withHour(12).withMinute(0));  // 距 = 410min
        LocalDateTime best = SplatoonRecordTool.pickClosest(times, t -> t, LocalTime.of(18, 50));
        eq("pickClosest(18:50)", best == null ? null : best.toLocalTime(), LocalTime.of(18, 45));

        System.out.println(fail == 0 ? "ALL PASS" : (fail + " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
