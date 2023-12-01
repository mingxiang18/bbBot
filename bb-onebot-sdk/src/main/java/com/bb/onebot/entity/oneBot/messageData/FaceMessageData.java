package com.bb.onebot.entity.oneBot.messageData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表情消息类型
 * @author ren
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FaceMessageData {
    /**
     * 表情ID
     * 码值见：https://github.com/richardchien/coolq-http-api/wiki/%E8%A1%A8%E6%83%85-CQ-%E7%A0%81-ID-%E8%A1%A8
     */
    private String id;
}
