package com.bb.onebot.entity.qqnt;

import lombok.Data;

import java.util.List;

/**
 * 消息原始信息
 * @author ren
 */
@Data
public class MessageRaw {
    private Long msgId;

    private List<MessageElement> elements;
}
