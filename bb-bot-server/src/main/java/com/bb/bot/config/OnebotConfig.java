package com.bb.bot.config;

import lombok.Data;

/**
 * onebot机器人相关配置
 */
@Data
public class OnebotConfig {
    /**
     * 是否开启
     */
    private boolean enable = false;
    /**
     * bot的qq号
     */
    private String qq;
    /**
     * bot的websocket配置
     */
    private WebSocketConfig webSocket;
}

