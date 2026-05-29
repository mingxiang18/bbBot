package com.bb.bot.aiAgent.tools;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SplatoonRecordTool 纯选择逻辑（modeVsIds / parseDateTime / pickByIndex / pickClosest 及筛选查表）
 * 的断言型单测，覆盖用户 5 个场景。
 *
 * <p>原 main() 自检脚本（T5.3 起）改造为 JUnit 断言型，纳入 reactor `mvn test` 自动跑。</p>
 */
class SplatoonRecordSelectorCheck {

    /* 场景2: 模式关键词 → vsModeId 列表 */

    @Test
    void modeVsIds_maps_keywords() {
        assertThat(SplatoonRecordTool.modeVsIds("真格"))
                .containsExactly(SplatoonRecordTool.SplatoonModes.ANARCHY_OPEN, SplatoonRecordTool.SplatoonModes.ANARCHY_CHALLENGE);
        assertThat(SplatoonRecordTool.modeVsIds("anarchy"))
                .containsExactly(SplatoonRecordTool.SplatoonModes.ANARCHY_OPEN, SplatoonRecordTool.SplatoonModes.ANARCHY_CHALLENGE);
        assertThat(SplatoonRecordTool.modeVsIds("占地")).containsExactly(SplatoonRecordTool.SplatoonModes.TURF);
        assertThat(SplatoonRecordTool.modeVsIds("x")).containsExactly(SplatoonRecordTool.SplatoonModes.X);
        assertThat(SplatoonRecordTool.modeVsIds(null)).isEmpty();
    }

    /* 场景3: 日期+时间解析（今天/昨天/前天/具体日期） */

    @Test
    void parseDateTime_relative_and_absolute() {
        LocalDate today = LocalDateTime.now().toLocalDate();
        assertThat(SplatoonRecordTool.parseDateTime("18:50")).isEqualTo(LocalDateTime.of(today, LocalTime.of(18, 50)));
        assertThat(SplatoonRecordTool.parseDateTime("今天大概18:50左右")).isEqualTo(LocalDateTime.of(today, LocalTime.of(18, 50)));
        assertThat(SplatoonRecordTool.parseDateTime("昨天19:00")).isEqualTo(LocalDateTime.of(today.minusDays(1), LocalTime.of(19, 0)));
        assertThat(SplatoonRecordTool.parseDateTime("前天20点10")).isEqualTo(LocalDateTime.of(today.minusDays(2), LocalTime.of(20, 10)));
        assertThat(SplatoonRecordTool.parseDateTime("2天前14:00")).isEqualTo(LocalDateTime.of(today.minusDays(2), LocalTime.of(14, 0)));
        assertThat(SplatoonRecordTool.parseDateTime("2026-05-24 18:50")).isEqualTo(LocalDateTime.of(LocalDate.of(2026, 5, 24), LocalTime.of(18, 50)));
        assertThat(SplatoonRecordTool.parseDateTime("随便")).isNull();
    }

    /* 筛选: 胜负/通关/鳞片 */

    @Test
    void filters_judgement_clear_scale() {
        assertThat(SplatoonRecordTool.battleJudgement("胜")).isEqualTo("WIN");
        assertThat(SplatoonRecordTool.battleJudgement("输了")).isEqualTo("LOSE");
        assertThat(SplatoonRecordTool.battleJudgement(null)).isNull();
        assertThat(SplatoonRecordTool.coopClear("成功")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("失败")).isFalse();
        assertThat(SplatoonRecordTool.coopClear(null)).isNull();
        assertThat(SplatoonRecordTool.scaleKey("我只要出了金鳞片的")).isEqualTo("gold");
        assertThat(SplatoonRecordTool.scaleKey("银鳞片")).isEqualTo("silver");
        assertThat(SplatoonRecordTool.scaleKey("gold")).isEqualTo("gold");
        assertThat(SplatoonRecordTool.scaleKey(null)).isNull();
    }

    /* 场景1: 最近第N场 → 已降序列表取第N个(1基) */

    @Test
    void pickByIndex_one_based() {
        List<String> recs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            recs.add("rec" + i); // rec1=最近
        }
        assertThat(SplatoonRecordTool.pickByIndex(recs, 4)).isEqualTo("rec4");
        assertThat(SplatoonRecordTool.pickByIndex(recs, 1)).isEqualTo("rec1");
        assertThat(SplatoonRecordTool.pickByIndex(recs, 99)).isNull();
    }

    /* 场景3: 最接近某时刻的一条 */

    @Test
    void pickClosest_minimizes_minute_distance() {
        LocalDate today = LocalDateTime.now().toLocalDate();
        LocalDateTime base = today.atStartOfDay();
        List<LocalDateTime> times = new ArrayList<>();
        times.add(base.withHour(20).withMinute(10)); // 距 18:50 = 80min
        times.add(base.withHour(18).withMinute(45)); // 距 = 5min  <-- 最近
        times.add(base.withHour(12).withMinute(0));  // 距 = 410min
        LocalDateTime best = SplatoonRecordTool.pickClosest(times, t -> t, LocalDateTime.of(today, LocalTime.of(18, 50)));
        assertThat(best).isNotNull();
        assertThat(best.toLocalTime()).isEqualTo(LocalTime.of(18, 45));
    }
}
