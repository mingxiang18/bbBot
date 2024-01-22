package com.bb.onebot.config;

import com.bb.onebot.handler.BotEventHandler;
import com.bb.onebot.handler.OneBotEventHandler;
import com.bb.onebot.handler.QqBotEventHandler;
import com.bb.onebot.handler.QqntBotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 机器人事件处理者配置类
 */
@Slf4j
@Configuration
public class BotEventHandlerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = "onebot")
    public BotEventHandler getOneBotEventHandler() {
        return new OneBotEventHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = "qqnt")
    public BotEventHandler getQqntBotEventHandler() {
        return new QqntBotEventHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = "qq")
    public BotEventHandler getQqBotEventHandler() {
        return new QqBotEventHandler();
    }
}
