package com.bb.onebot.api.impl;

import com.alibaba.fastjson2.JSON;
import com.bb.onebot.connection.OneBotWebSocketClient;
import com.bb.onebot.entity.Action;
import com.bb.onebot.entity.Message;
import com.bb.onebot.entity.MessageContent;
import com.bb.onebot.api.ActionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 发起动作请求Api实现类
 * @author ren
 */
@Component
public class ActionApiImpl implements ActionApi {

    @Autowired
    private OneBotWebSocketClient webSocketClient;

    @Override
    public boolean sendPrivateMessage(String userId, String message) {
        return sendPrivateMessage(userId, MessageContent.buildTextContent(message));
    }

    @Override
    public boolean sendPrivateMessage(String userId, MessageContent messageContent) {
        return sendPrivateMessage(userId, Arrays.asList(messageContent));
    }

    @Override
    public boolean sendPrivateMessage(String userId, List<MessageContent> messageContentList) {
        Action action = Action.buildPrivateMessageSendAction(Message.builder()
                .userId(userId)
                .message(messageContentList)
                .build());
        if (webSocketClient.hasConnection.get()) {
            webSocketClient.send(JSON.toJSONString(action));
            return true;
        }
        return false;
    }

    @Override
    public boolean sendGroupMessage(String groupId, String message) {
        return sendGroupMessage(groupId, MessageContent.buildTextContent(message));
    }

    @Override
    public boolean sendGroupMessage(String groupId, MessageContent messageContent) {
        return sendGroupMessage(groupId, Arrays.asList(messageContent));
    }

    @Override
    public boolean sendGroupMessage(String groupId, List<MessageContent> messageContentList) {
        Action action = Action.buildGroupMessageSendAction(Message.builder()
                .groupId(groupId)
                .message(messageContentList)
                .build());
        if (webSocketClient.hasConnection.get()) {
            webSocketClient.send(JSON.toJSONString(action));
            return true;
        }
        return false;
    }
}
