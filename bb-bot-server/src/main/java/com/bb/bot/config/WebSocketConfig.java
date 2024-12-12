package com.bb.bot.config;

import lombok.Data;

/**
 * WebSocket配置类
 */
@Data
public class WebSocketConfig {
    /**
     * WebSocket服务端配置
     */
    private WebsocketServerConfig server;
    /**
     * WebSocket客户端配置
     */
    private WebsocketClientConfig client;
}

