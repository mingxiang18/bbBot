package com.bb.bot.entity.qqnt;

import lombok.Data;

/**
 * 消息内部元素
 * @author ren
 */
@Data
public class MessageElement {
    /**
     * 元素类型，疑似 1-文本，2-图片
     */
    private Integer elementType;

    private String elementId;

    private String msgId;

    private TextElement textElement;

    private PicElement picElement;
}
