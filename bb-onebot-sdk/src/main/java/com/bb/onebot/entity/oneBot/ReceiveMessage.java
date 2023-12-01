package com.bb.onebot.entity.oneBot;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * @author ren
 */
@Data
public class ReceiveMessage {
    /**
     * 消息类型
     * private-私人，group-群组
     */
    @JSONField(name = "message_type")
    private String messageType;

    @JSONField(name = "user_id")
    private String userId;

    @JSONField(name = "group_id")
    private String groupId;

    @JSONField(name = "message_seq")
    private Long messageSeq;

    @JSONField(name = "message_id")
    private Long messageId;

    @JSONField(name = "message")
    private String message;

    @JSONField(name = "time")
    private Long time;

    @JSONField(name = "sender")
    private ReceiveMessageSender sender;
}
