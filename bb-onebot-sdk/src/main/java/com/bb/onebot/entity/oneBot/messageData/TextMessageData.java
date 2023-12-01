package com.bb.onebot.entity.oneBot.messageData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本消息类型
 * @author ren
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TextMessageData {
    /**
     * 文本
     */
    private String text;
}
