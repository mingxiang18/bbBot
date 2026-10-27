package com.bb.bot.config;

import com.alibaba.fastjson2.annotation.JSONField;
import com.bb.bot.connection.onebot.OneBotSession;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

/** OneBot 机器人配置；每个配置实例维护独立连接和 API 等待队列。 */
@Data
public class OnebotConfig {

    /** 是否开启该机器人。 */
    private boolean enable = false;

    /** 机器人 QQ 号，用于握手校验和过滤其它账号的事件。 */
    private String qq;

    /** OneBot Bearer token，不参与 toString，避免日志输出凭据。 */
    @ToString.Exclude
    private String accessToken;

    /** API 回执等待时间，单位毫秒；超时后抛错，不自动重发。 */
    private long apiTimeoutMs = 15_000;

    /** 运行时配置名，用于区分多个机器人，不序列化到消息数据中。 */
    @JsonIgnore
    @JSONField(serialize = false, deserialize = false)
    private String botName;

    /** 连接、线程和等待请求属于运行时状态，不能随消息信封序列化。 */
    @ToString.Exclude
    @JsonIgnore
    @JSONField(serialize = false, deserialize = false)
    private final OneBotSession session = new OneBotSession();

    /** 正向客户端或反向服务端配置，两者只能启用一种。 */
    private WebSocketConfig webSocket;
}
