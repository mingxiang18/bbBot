package com.bb.bot.config;

import com.bb.bot.constant.BotType;
import com.bb.bot.handler.BotEventHandler;
import com.bb.bot.handler.onbot.OneBotEventHandler;
import com.bb.bot.handler.qq.QqBotEventHandler;
import com.bb.bot.handler.qqnt.QqntBotEventHandler;
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
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.ONEBOT)
    public BotEventHandler getOneBotEventHandler() {
        return new OneBotEventHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.QQNT)
    public BotEventHandler getQqntBotEventHandler() {
        return new QqntBotEventHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.QQ)
    public BotEventHandler getQqBotEventHandler() {
        return new QqBotEventHandler();
    }
}
