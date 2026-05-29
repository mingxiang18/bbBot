package com.bb.bot.handler.splatoon.render;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 喷喷战绩两套渲染器（{@link SplatoonHtmlRenderer} HTML→图、{@link SplatoonRecordRenderer} Graphics2D）
 * 共用的配色/模式映射表。原本两边各维护一份模式表、规则图标表、段位升降箭头表，值完全一致，
 * 现收敛到此处作为单一数据源。
 *
 * <p>模式左条颜色以 {@link java.awt.Color} 为权威值；HTML 渲染器需要的十六进制串由 {@link #toHex(Color)}
 * 派生，保证两种视图永远等价。规则图标表、箭头表为纯字符串映射，两边直接共用。</p>
 *
 * <p>注意：不收敛 {@code SplatoonRecordRenderer.ruleMap}（打工规则中文名）与
 * {@code SplatoonHtmlRenderer.coopRuleName}——两者的中文文案本就不同，合并会改变行为；
 * 队伍样式 {@code teamStyleMap} 仅 Record 渲染器使用，亦不在共享范围内。</p>
 */
public final class SplatoonStyleConfig {

    private SplatoonStyleConfig() {
    }

    /** 模式 id → 模式样式（中文名 / 左条颜色 / 模式图标路径）。两渲染器共用。 */
    public static final Map<String, ModeStyle> MODE_STYLE = new LinkedHashMap<>();

    /** 规则 id → 规则图标路径。两渲染器共用（原 RULE_ICON / battleRuleMap）。 */
    public static final Map<String, String> RULE_ICON = new LinkedHashMap<>();

    /** 段位升降标记 → 箭头字符。两渲染器共用（原 DIFF / pointDiffMap）。 */
    public static final Map<String, String> POINT_DIFF = new LinkedHashMap<>();

    static {
        MODE_STYLE.put("VnNNb2RlLTE=", new ModeStyle("VnNNb2RlLTE=", "占地比赛", new Color(95, 255, 26), "nso_splatoon/battle/mode/regular.png"));
        MODE_STYLE.put("VnNNb2RlLTUx", new ModeStyle("VnNNb2RlLTUx", "蛮颓比赛(开放)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        MODE_STYLE.put("VnNNb2RlLTI=", new ModeStyle("VnNNb2RlLTI=", "蛮颓比赛(挑战)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        MODE_STYLE.put("VnNNb2RlLTQ=", new ModeStyle("VnNNb2RlLTQ=", "活动比赛", new Color(255, 0, 98), "nso_splatoon/battle/mode/event.png"));
        MODE_STYLE.put("VnNNb2RlLTU=", new ModeStyle("VnNNb2RlLTU=", "私人比赛", new Color(149, 0, 255), "nso_splatoon/battle/mode/private.png"));
        MODE_STYLE.put("VnNNb2RlLTM=", new ModeStyle("VnNNb2RlLTM=", "X比赛", new Color(0, 131, 98), "nso_splatoon/battle/mode/x.png"));
        MODE_STYLE.put("VnNNb2RlLTY=", new ModeStyle("VnNNb2RlLTY=", "祭典比赛", new Color(34, 220, 255, 255), "nso_splatoon/battle/mode/fest.png"));
        MODE_STYLE.put("VnNNb2RlLTg=", new ModeStyle("VnNNb2RlLTg=", "三色夺宝比赛", new Color(34, 255, 248, 255), "nso_splatoon/battle/mode/fest.png"));

        RULE_ICON.put("VnNSdWxlLTI=", "nso_splatoon/battle/rule/ta.png");
        RULE_ICON.put("VnNSdWxlLTE=", "nso_splatoon/battle/rule/quyu.png");
        RULE_ICON.put("VnNSdWxlLTM=", "nso_splatoon/battle/rule/yuhu.png");
        RULE_ICON.put("VnNSdWxlLTQ=", "nso_splatoon/battle/rule/geli.png");

        POINT_DIFF.put("UP", "↑");
        POINT_DIFF.put("DOWN", "↓");
        POINT_DIFF.put("KEEP", "→");
    }

    /**
     * HTML 渲染器使用的模式视图：{@code [中文名, 左条颜色十六进制串, 模式图标路径]}。
     * 颜色由 {@link ModeStyle#getColor()} 派生，与 Graphics2D 视图同源。
     */
    public static String[] htmlMode(String modeId) {
        ModeStyle s = MODE_STYLE.get(modeId);
        if (s == null) {
            return null;
        }
        return new String[]{s.getModeName(), toHex(s.getColor()), s.getModeImgPath()};
    }

    /** {@link Color} → {@code #rrggbb}（忽略 alpha，与原 HTML 渲染器手写的六位串一致）。 */
    public static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** 喷喷模式绘制配置实体类 */
    @Data
    @AllArgsConstructor
    public static class ModeStyle {
        private String modeId;
        private String modeName;
        private Color color;
        private String modeImgPath;
    }
}
