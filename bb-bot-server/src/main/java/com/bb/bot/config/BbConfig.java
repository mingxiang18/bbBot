package com.bb.bot.config;

import lombok.Data;

/**
 * bb机器人相关配置
 */
@Data
public class BbConfig {
    /**
     * 是否开启
     */
    private boolean enable = false;

    /**
     * 端口号
     */
    private int port;

    /**
     * 服务端身份验证密钥
     */
    private String appId;
    private String secret;
}

