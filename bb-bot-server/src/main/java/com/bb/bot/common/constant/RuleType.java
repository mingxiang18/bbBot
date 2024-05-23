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
     * 占用模式，在指定方法开启占用后，指定群组或个人全部消息都会被该方法接管处理，直到占用关闭，如开启海龟汤模式
     */
    public static final String OCCUPATION = "occupation";

    /**
     * 未满足任何规则时,采用该规则默认回复
     */
    public static final String DEFAULT = "default";
}
