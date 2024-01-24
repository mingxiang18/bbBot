package com.bb.bot.entity.qqnt;

import lombok.Data;

/**
 * 文本元素
 * @author ren
 */
@Data
public class TextElement {
    private String content;

    /**
     * at类型，疑似 0-没有at，2-有at
     */
    private String atType;

    private String atUid;

    private String atNtUid;
}
