package com.bb.bot.entity.bb;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * bb服务端发送消息对象
 * @author ren
 */
@Data
@NoArgsConstructor
public class BbSocketServerMessage {
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
     * 要回复的消息唯一id
     */
    private String receiveMessageId;
    /**
     * 消息内容
     */
    private List<BbMessageContent> messageList = new ArrayList<>();
}
