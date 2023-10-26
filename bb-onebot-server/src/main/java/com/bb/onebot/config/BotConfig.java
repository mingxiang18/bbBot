package com.bb.onebot.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 机器人相关配置
 */
@Slf4j
@Data
@Configuration
public class BotConfig {

    @Value("${bot.qq}")
    private String qq;
}
