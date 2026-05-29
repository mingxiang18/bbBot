package com.bb.bot.handler.splatoon.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7.5：断言收敛后的 {@link SplatoonStyleConfig} 映射值与两个渲染器迁移前各自硬编码的值逐一相等，
 * 同时验证两套渲染器经收敛后引用到同一份数据。
 */
class SplatoonStyleConfigTest {

    /* ---- 迁移前 SplatoonRecordRenderer 中各表的原始字面量（基线快照） ---- */

    private static Map<String, SplatoonStyleConfig.ModeStyle> originalModeStyleMap() {
        Map<String, SplatoonStyleConfig.ModeStyle> m = new LinkedHashMap<>();
        m.put("VnNNb2RlLTE=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTE=", "占地比赛", new Color(95, 255, 26), "nso_splatoon/battle/mode/regular.png"));
        m.put("VnNNb2RlLTUx", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTUx", "蛮颓比赛(开放)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        m.put("VnNNb2RlLTI=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTI=", "蛮颓比赛(挑战)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        m.put("VnNNb2RlLTQ=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTQ=", "活动比赛", new Color(255, 0, 98), "nso_splatoon/battle/mode/event.png"));
        m.put("VnNNb2RlLTU=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTU=", "私人比赛", new Color(149, 0, 255), "nso_splatoon/battle/mode/private.png"));
        m.put("VnNNb2RlLTM=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTM=", "X比赛", new Color(0, 131, 98), "nso_splatoon/battle/mode/x.png"));
        m.put("VnNNb2RlLTY=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTY=", "祭典比赛", new Color(34, 220, 255, 255), "nso_splatoon/battle/mode/fest.png"));
        m.put("VnNNb2RlLTg=", new SplatoonStyleConfig.ModeStyle("VnNNb2RlLTg=", "三色夺宝比赛", new Color(34, 255, 248, 255), "nso_splatoon/battle/mode/fest.png"));
        return m;
    }

    private static Map<String, String> originalBattleRuleMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("VnNSdWxlLTI=", "nso_splatoon/battle/rule/ta.png");
        m.put("VnNSdWxlLTE=", "nso_splatoon/battle/rule/quyu.png");
        m.put("VnNSdWxlLTM=", "nso_splatoon/battle/rule/yuhu.png");
        m.put("VnNSdWxlLTQ=", "nso_splatoon/battle/rule/geli.png");
        return m;
    }

    private static Map<String, String> originalPointDiffMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("UP", "↑");
        m.put("DOWN", "↓");
        m.put("KEEP", "→");
        return m;
    }

    /* ---- 迁移前 SplatoonHtmlRenderer 中 MODE 表的原始字面量（hex 视图） ---- */

    private static Map<String, String[]> originalHtmlModeMap() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("VnNNb2RlLTE=", new String[]{"占地比赛", "#5fff1a", "nso_splatoon/battle/mode/regular.png"});
        m.put("VnNNb2RlLTUx", new String[]{"蛮颓比赛(开放)", "#ff3c1a", "nso_splatoon/battle/mode/rank.png"});
        m.put("VnNNb2RlLTI=", new String[]{"蛮颓比赛(挑战)", "#ff3c1a", "nso_splatoon/battle/mode/rank.png"});
        m.put("VnNNb2RlLTQ=", new String[]{"活动比赛", "#ff0062", "nso_splatoon/battle/mode/event.png"});
        m.put("VnNNb2RlLTU=", new String[]{"私人比赛", "#9500ff", "nso_splatoon/battle/mode/private.png"});
        m.put("VnNNb2RlLTM=", new String[]{"X比赛", "#008362", "nso_splatoon/battle/mode/x.png"});
        m.put("VnNNb2RlLTY=", new String[]{"祭典比赛", "#22dcff", "nso_splatoon/battle/mode/fest.png"});
        m.put("VnNNb2RlLTg=", new String[]{"三色夺宝比赛", "#22fff8", "nso_splatoon/battle/mode/fest.png"});
        return m;
    }

    @Test
    void modeStyle_matchesOriginalRecordRendererValues() {
        Map<String, SplatoonStyleConfig.ModeStyle> original = originalModeStyleMap();
        assertThat(SplatoonStyleConfig.MODE_STYLE.keySet()).containsExactlyElementsOf(original.keySet());
        original.forEach((id, exp) -> {
            SplatoonStyleConfig.ModeStyle act = SplatoonStyleConfig.MODE_STYLE.get(id);
            assertThat(act.getModeId()).isEqualTo(exp.getModeId());
            assertThat(act.getModeName()).isEqualTo(exp.getModeName());
            assertThat(act.getColor()).isEqualTo(exp.getColor());
            assertThat(act.getModeImgPath()).isEqualTo(exp.getModeImgPath());
        });
    }

    @Test
    void htmlMode_matchesOriginalHtmlRendererValues() {
        Map<String, String[]> original = originalHtmlModeMap();
        original.forEach((id, exp) -> {
            String[] act = SplatoonStyleConfig.htmlMode(id);
            assertThat(act).as("htmlMode for %s", id).containsExactly(exp);
        });
    }

    @Test
    void htmlMode_returnsNullForUnknownId() {
        assertThat(SplatoonStyleConfig.htmlMode("not-a-mode")).isNull();
    }

    @Test
    void ruleIcon_matchesOriginalValues() {
        assertThat(SplatoonStyleConfig.RULE_ICON).containsExactlyInAnyOrderEntriesOf(originalBattleRuleMap());
    }

    @Test
    void pointDiff_matchesOriginalValues() {
        assertThat(SplatoonStyleConfig.POINT_DIFF).containsExactlyInAnyOrderEntriesOf(originalPointDiffMap());
    }

    @Test
    void toHex_dropsAlphaAndMatchesHandwrittenStrings() {
        assertThat(SplatoonStyleConfig.toHex(new Color(95, 255, 26))).isEqualTo("#5fff1a");
        assertThat(SplatoonStyleConfig.toHex(new Color(0, 131, 98))).isEqualTo("#008362");
        // alpha 应被忽略（祭典模式带 alpha=255）
        assertThat(SplatoonStyleConfig.toHex(new Color(34, 220, 255, 255))).isEqualTo("#22dcff");
    }

    @Test
    void bothRenderersShareTheSameUnderlyingTables() {
        // RecordRenderer 的静态表收敛后即 config 同一引用
        assertThat(SplatoonRecordRenderer.modeStyleMap).isSameAs(SplatoonStyleConfig.MODE_STYLE);
        assertThat(SplatoonRecordRenderer.battleRuleMap).isSameAs(SplatoonStyleConfig.RULE_ICON);
        assertThat(SplatoonRecordRenderer.pointDiffMap).isSameAs(SplatoonStyleConfig.POINT_DIFF);
    }
}
