package com.bb.bot.client;

import com.bb.bot.entity.bb.BbSocketServerMessage;
import org.java_websocket.WebSocket;

/**
 * 客户端消息处理者
 */
public interface BbClientMessageHandler {

    /**
     * 处理消息
     */
    public void handleMessage(WebSocket webSocket, BbSocketServerMessage message);
}
