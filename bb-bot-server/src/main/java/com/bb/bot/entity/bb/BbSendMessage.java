package com.bb.bot.entity.bb;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

/**
 * bb公共接收消息对象
 * @author ren
 */
@Data
@NoArgsConstructor
public class BbSendMessage {
    /**
     * 机器人类型
     */
    private String botType;
    /**
     * webSocket连接
     */
    private WebSocket webSocket;
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

    public BbSendMessage(BbReceiveMessage bbReceiveMessage) {
        this.botType = bbReceiveMessage.getBotType();
        this.webSocket = bbReceiveMessage.getWebSocket();
        this.messageType = bbReceiveMessage.getMessageType();
        this.userId = bbReceiveMessage.getUserId();
        this.groupId = bbReceiveMessage.getGroupId();
        this.receiveMessageId = bbReceiveMessage.getMessageId();
    }
}
