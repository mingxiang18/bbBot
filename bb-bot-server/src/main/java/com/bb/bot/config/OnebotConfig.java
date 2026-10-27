package com.bb.bot.config;

import lombok.Data;
import lombok.ToString;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.bb.bot.connection.onebot.OneBotSession;

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
    /** OneBot Bearer token; never include it in logs. */
    @ToString.Exclude
    private String accessToken;
    private long apiTimeoutMs = 15000;
    @JsonIgnore
    @JSONField(serialize = false, deserialize = false)
    private String botName;
    @ToString.Exclude
    @JsonIgnore
    @JSONField(serialize = false, deserialize = false)
    private final OneBotSession session = new OneBotSession();
    /**
     * bot的websocket配置
     */
    private WebSocketConfig webSocket;
}

