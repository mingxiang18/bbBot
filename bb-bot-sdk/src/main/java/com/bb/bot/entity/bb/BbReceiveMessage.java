package com.bb.bot.entity.bb;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * bb公共接收消息对象
 * @author ren
 */
@Data
public class BbReceiveMessage {
    /**
     * 消息类型
     * private-私人，group-群组
     */
    private String messageType;
    /**
     * 发送者id
     */
    private String userId;
    /**
     * 群组id
     */
    private String groupId;
    /**
     * 消息唯一id
     */
    private String messageId;
    /**
     * 消息文本内容
     */
    private String message;
    /**
     * 消息@对象
     */
    private List<MessageUser> atUserList = new ArrayList<>();
    /**
     * 消息发送时间
     */
    private LocalDateTime sendTime = LocalDateTime.now();
}
