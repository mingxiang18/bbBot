package com.bb.bot.config;

import lombok.Data;

/**
 * qq官方机器人相关配置
 */
@Data
public class QqConfig {
    /**
     * 是否开启
     */
    private boolean enable = false;
    /**
     * qq官方所需参数
     */
    private String appId;
    private String clientSecret;
}

