package com.bb.bot.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 机器人相关配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "bot")
public class BotConfig {

    private Map<String, OnebotConfig> onebot = new HashMap<>();

    private Map<String, QqConfig> qq = new HashMap<>();

    private Map<String, BbConfig> bb = new HashMap<>();
}
