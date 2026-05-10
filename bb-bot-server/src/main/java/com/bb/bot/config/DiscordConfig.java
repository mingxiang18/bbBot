package com.bb.bot.config;

import lombok.Data;
import net.dv8tion.jda.api.JDA;

/**
 * discord机器人相关配置
 */
@Data
public class DiscordConfig {
    /**
     * 是否开启
     */
    private boolean enable = false;
    /**
     * Discord Developer Portal生成的bot token
     */
    private String token;
    /**
     * bot用户id，可选；未配置时使用JDA登录后的self user id
     */
    private String botUserId;
    /**
     * 是否处理私聊消息
     */
    private boolean enableDirectMessage = true;
    /**
     * 自定义机器人名称
     */
    private transient String botName;
    /**
     * 运行时JDA客户端
     */
    private transient JDA jda;
}
