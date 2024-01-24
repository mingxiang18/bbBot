package com.bb.bot.api.qqnt.impl;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.api.qqnt.MessageApi;
import com.bb.bot.connection.BotWebSocketServer;
import com.bb.bot.entity.qqnt.Peer;
import com.bb.bot.entity.qqnt.QqntSendMessage;
import com.bb.bot.entity.qqnt.SendMessageElement;
import org.java_websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 发起动作请求Api实现类
 * @author ren
 */
@Component
public class MessageApiImpl implements MessageApi {

    @Autowired(required = false)
    private BotWebSocketServer webSocketServer;

    @Override
    public boolean sendMessage(Peer peer, String message) {
        Collection<WebSocket> webSocketConnections = webSocketServer.getConnections();
        QqntSendMessage qqntSendMessage = new QqntSendMessage();
        qqntSendMessage.setPeer(peer);
        qqntSendMessage.setMessageElementList(Arrays.asList(SendMessageElement.builder()
                        .type("text")
                        .content(message)
                        .build()));
        for (WebSocket webSocketConnection : webSocketConnections) {
            webSocketConnection.send(JSON.toJSONString(qqntSendMessage));
            break;
        }
        return true;
    }

    @Override
    public boolean sendMessage(Peer peer, SendMessageElement messageElement) {
        Collection<WebSocket> webSocketConnections = webSocketServer.getConnections();
        QqntSendMessage qqntSendMessage = new QqntSendMessage();
        qqntSendMessage.setPeer(peer);
        qqntSendMessage.setMessageElementList(Arrays.asList(messageElement));
        for (WebSocket webSocketConnection : webSocketConnections) {
            webSocketConnection.send(JSON.toJSONString(qqntSendMessage));
            break;
        }
        return true;
    }

    @Override
    public boolean sendMessage(Peer peer, List<SendMessageElement> messageElementList) {
        Collection<WebSocket> webSocketConnections = webSocketServer.getConnections();
        QqntSendMessage qqntSendMessage = new QqntSendMessage();
        qqntSendMessage.setPeer(peer);
        qqntSendMessage.setMessageElementList(messageElementList);
        for (WebSocket webSocketConnection : webSocketConnections) {
            webSocketConnection.send(JSON.toJSONString(qqntSendMessage));
            break;
        }
        return true;
    }
}
