package com.bb.bot.handler.splatoon;

/**
 * 喷喷战绩记录类型。承载打工/对战在指令关键字、默认区间、提示文案等维度的差异，
 * 供 {@link RecordRangeParser} 与（后续合并的）Handler/Tool 主流程共用。
 *
 * <p>各字段值与重构前 {@code BbSplatoonUserHandler} 两个记录方法中的内联字面量逐一等价：
 * <ul>
 *   <li>COOP（打工记录）：关键字「打工记录」、默认区间 1-5、跨度上限 20、缺数据提示「还没有打工记录…」</li>
 *   <li>BATTLE（对战记录）：关键字「对战记录」、默认区间 1-5、跨度上限 20、缺数据提示「还没有对战记录…」</li>
 * </ul>
 */
public enum RecordType {

    /** 打工记录。 */
    COOP("打工记录",
            "格式不正确，参考格式：【打工记录】、【打工记录2-11】",
            "还没有打工记录，先发【上传打工记录】或等自动上传跑过一轮"),

    /** 对战记录。 */
    BATTLE("对战记录",
            "格式不正确，参考格式：【对战记录】、【对战记录2-11】",
            "还没有对战记录，先发【上传对战记录】或等自动上传跑过一轮");

    /** 指令关键字（不含可选前导斜杠与区间数字），如「打工记录」。 */
    private final String keyword;

    /** 区间格式错误时回复的提示文案。 */
    private final String formatErrorHint;

    /** 查无记录时回复的提示文案。 */
    private final String emptyHint;

    RecordType(String keyword, String formatErrorHint, String emptyHint) {
        this.keyword = keyword;
        this.formatErrorHint = formatErrorHint;
        this.emptyHint = emptyHint;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getFormatErrorHint() {
        return formatErrorHint;
    }

    public String getEmptyHint() {
        return emptyHint;
    }
}
