package com.bb.bot.common.constant;

/**
 * 规则类型常量
 * @author ren
 */
public class RuleType {
    /**
     * 完全匹配
     */
    public static final String MATCH = "match";

    /**
     * 模糊匹配
     */
    public static final String FUZZY = "fuzzy";

    /**
     * 正则匹配
     */
    public static final String REGEX = "regex";

    /**
     * 未满足任何规则时,采用该规则默认回复
     */
    public static final String DEFAULT = "default";
}
