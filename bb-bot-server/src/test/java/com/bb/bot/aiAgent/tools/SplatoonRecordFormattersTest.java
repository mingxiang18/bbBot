package com.bb.bot.aiAgent.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T9.3：SplatoonRecordTool 各 formatter/查表方法去重后的全分支等价断言。
 * 覆盖 battleJudgement/coopClear/scaleKey 的 WIN/LOSE/TRUE/FALSE/各色/null 分支,
 * 以及 formatDesc + selectorDesc/coopDesc/battleDesc 的拼接与空 parts 行为。
 */
class SplatoonRecordFormattersTest {

    /* ---------------- battleJudgement ---------------- */

    @Test
    void battleJudgement_win_branch() {
        assertThat(SplatoonRecordTool.battleJudgement("win")).isEqualTo("WIN");
        assertThat(SplatoonRecordTool.battleJudgement("WIN")).isEqualTo("WIN");
        assertThat(SplatoonRecordTool.battleJudgement("胜")).isEqualTo("WIN");
        assertThat(SplatoonRecordTool.battleJudgement("赢了")).isEqualTo("WIN");
        assertThat(SplatoonRecordTool.battleJudgement("  胜利  ")).isEqualTo("WIN");
    }

    @Test
    void battleJudgement_lose_branch() {
        assertThat(SplatoonRecordTool.battleJudgement("lose")).isEqualTo("LOSE");
        assertThat(SplatoonRecordTool.battleJudgement("负")).isEqualTo("LOSE");
        assertThat(SplatoonRecordTool.battleJudgement("输了")).isEqualTo("LOSE");
        assertThat(SplatoonRecordTool.battleJudgement("失败")).isEqualTo("LOSE");
    }

    @Test
    void battleJudgement_null_and_blank_and_unknown() {
        assertThat(SplatoonRecordTool.battleJudgement(null)).isNull();
        assertThat(SplatoonRecordTool.battleJudgement("")).isNull();
        assertThat(SplatoonRecordTool.battleJudgement("   ")).isNull();
        assertThat(SplatoonRecordTool.battleJudgement("平局")).isNull();
    }

    /* ---------------- coopClear ---------------- */

    @Test
    void coopClear_true_branch() {
        assertThat(SplatoonRecordTool.coopClear("clear")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("通关")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("成功")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("胜")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("win")).isTrue();
        assertThat(SplatoonRecordTool.coopClear("全清")).isTrue();
    }

    @Test
    void coopClear_false_branch() {
        assertThat(SplatoonRecordTool.coopClear("fail")).isFalse();
        assertThat(SplatoonRecordTool.coopClear("失败")).isFalse();
        assertThat(SplatoonRecordTool.coopClear("输")).isFalse();
        assertThat(SplatoonRecordTool.coopClear("败")).isFalse();
        assertThat(SplatoonRecordTool.coopClear("没过")).isFalse();
        assertThat(SplatoonRecordTool.coopClear("没通")).isFalse();
    }

    @Test
    void coopClear_null_and_blank_and_unknown() {
        assertThat(SplatoonRecordTool.coopClear(null)).isNull();
        assertThat(SplatoonRecordTool.coopClear("")).isNull();
        assertThat(SplatoonRecordTool.coopClear("   ")).isNull();
        assertThat(SplatoonRecordTool.coopClear("打工")).isNull();
    }

    /* ---------------- scaleKey ---------------- */

    @Test
    void scaleKey_all_colors() {
        assertThat(SplatoonRecordTool.scaleKey("金")).isEqualTo("gold");
        assertThat(SplatoonRecordTool.scaleKey("gold")).isEqualTo("gold");
        assertThat(SplatoonRecordTool.scaleKey("我只要出了金鳞片的")).isEqualTo("gold");
        assertThat(SplatoonRecordTool.scaleKey("银")).isEqualTo("silver");
        assertThat(SplatoonRecordTool.scaleKey("silver")).isEqualTo("silver");
        assertThat(SplatoonRecordTool.scaleKey("铜")).isEqualTo("bronze");
        assertThat(SplatoonRecordTool.scaleKey("bronze")).isEqualTo("bronze");
    }

    @Test
    void scaleKey_null_and_blank_and_unknown() {
        assertThat(SplatoonRecordTool.scaleKey(null)).isNull();
        assertThat(SplatoonRecordTool.scaleKey("")).isNull();
        assertThat(SplatoonRecordTool.scaleKey("   ")).isNull();
        assertThat(SplatoonRecordTool.scaleKey("鳞片")).isNull();
    }

    /* ---------------- formatDesc ---------------- */

    @Test
    void formatDesc_empty_parts_returns_empty_string() {
        assertThat(SplatoonRecordTool.formatDesc(List.of())).isEmpty();
    }

    @Test
    void formatDesc_single_part() {
        assertThat(SplatoonRecordTool.formatDesc(List.of("胜"))).isEqualTo("(胜)");
    }

    @Test
    void formatDesc_multiple_parts_joined_with_slash() {
        assertThat(SplatoonRecordTool.formatDesc(List.of("真格", "胜", "出boss"))).isEqualTo("(真格/胜/出boss)");
    }

    /* ---------------- selectorDesc (private, via reflection) ---------------- */

    private final SplatoonRecordTool tool = new SplatoonRecordTool();

    private String selectorDesc(int idx, LocalDateTime time, Boolean bossOnly, String mode) throws Exception {
        Method m = SplatoonRecordTool.class.getDeclaredMethod(
                "selectorDesc", int.class, LocalDateTime.class, Boolean.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(tool, idx, time, bossOnly, mode);
    }

    private String coopDesc(Boolean bossOnly, Boolean clear, String scaleKey) throws Exception {
        Method m = SplatoonRecordTool.class.getDeclaredMethod(
                "coopDesc", Boolean.class, Boolean.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(tool, bossOnly, clear, scaleKey);
    }

    private String battleDesc(String mode, String judgement) throws Exception {
        Method m = SplatoonRecordTool.class.getDeclaredMethod("battleDesc", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(tool, mode, judgement);
    }

    @Test
    void selectorDesc_index_only() throws Exception {
        assertThat(selectorDesc(3, null, null, null)).isEqualTo("(第3场)");
    }

    @Test
    void selectorDesc_time_overrides_index() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 5, 24, 18, 50);
        assertThat(selectorDesc(3, t, null, null)).isEqualTo("(约 05-24 18:50)");
    }

    @Test
    void selectorDesc_all_parts() throws Exception {
        assertThat(selectorDesc(2, null, Boolean.TRUE, "真格")).isEqualTo("(第2场/出boss/真格)");
    }

    @Test
    void selectorDesc_bossOnly_false_and_blank_mode_skipped() throws Exception {
        assertThat(selectorDesc(1, null, Boolean.FALSE, "  ")).isEqualTo("(第1场)");
    }

    /* ---------------- coopDesc ---------------- */

    @Test
    void coopDesc_empty_when_all_null() throws Exception {
        assertThat(coopDesc(null, null, null)).isEmpty();
        assertThat(coopDesc(Boolean.FALSE, null, null)).isEmpty();
    }

    @Test
    void coopDesc_clear_true() throws Exception {
        assertThat(coopDesc(Boolean.TRUE, Boolean.TRUE, "gold")).isEqualTo("(出boss/通关/出金鳞片)");
    }

    @Test
    void coopDesc_clear_false_silver() throws Exception {
        assertThat(coopDesc(null, Boolean.FALSE, "silver")).isEqualTo("(失败/出银鳞片)");
    }

    @Test
    void coopDesc_bronze_only() throws Exception {
        assertThat(coopDesc(null, null, "bronze")).isEqualTo("(出铜鳞片)");
    }

    /* ---------------- battleDesc ---------------- */

    @Test
    void battleDesc_empty_when_all_blank_or_null() throws Exception {
        assertThat(battleDesc(null, null)).isEmpty();
        assertThat(battleDesc("   ", "DRAW")).isEmpty();
    }

    @Test
    void battleDesc_mode_and_win() throws Exception {
        assertThat(battleDesc("真格", "WIN")).isEqualTo("(真格/胜)");
    }

    @Test
    void battleDesc_lose_only() throws Exception {
        assertThat(battleDesc(null, "LOSE")).isEqualTo("(负)");
    }

    @Test
    void battleDesc_mode_only() throws Exception {
        assertThat(battleDesc("x", null)).isEqualTo("(x)");
    }

    /* ---------------- modeVsIds (table unchanged sanity) ---------------- */

    @Test
    void modeVsIds_still_maps_correctly() {
        assertThat(SplatoonRecordTool.modeVsIds("真格"))
                .containsExactly(SplatoonRecordTool.SplatoonModes.ANARCHY_OPEN, SplatoonRecordTool.SplatoonModes.ANARCHY_CHALLENGE);
        assertThat(SplatoonRecordTool.modeVsIds(null)).isEmpty();
    }
}
