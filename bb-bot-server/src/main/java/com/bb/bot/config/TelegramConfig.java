package com.bb.bot.config;

import lombok.Data;

/**
 * telegram机器人相关配置
 */
@Data
public class TelegramConfig {
    /**
     * 是否开启
     */
    private boolean enable = false;
    /**
     * BotFather生成的bot token
     */
    private String token;
    /**
     * bot用户名，不带@
     */
    private String botUsername;
    /**
     * telegram api地址
     */
    private String baseUrl = "https://api.telegram.org";
    /**
     * webhook secret token，用于校验X-Telegram-Bot-Api-Secret-Token请求头
     */
    private String secretToken;
}
