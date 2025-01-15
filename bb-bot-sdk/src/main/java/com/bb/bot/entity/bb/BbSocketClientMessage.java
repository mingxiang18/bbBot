package com.bb.bot.entity.bb;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * bb客户端发送消息对象
 * @author ren
 */
@Data
public class BbSocketClientMessage {
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
     * 消息发送者详情
     */
    private MessageUser sender;
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
