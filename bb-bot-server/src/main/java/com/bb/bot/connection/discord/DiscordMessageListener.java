package com.bb.bot.connection.discord;

import com.bb.bot.common.util.discord.DiscordMessageUtil;
import com.bb.bot.config.DiscordConfig;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

/**
 * discord消息监听器
 */
@Slf4j
public class DiscordMessageListener extends ListenerAdapter {

    private final String botName;
    private final DiscordConfig discordConfig;
    private final ApplicationEventPublisher publisher;

    public DiscordMessageListener(String botName, DiscordConfig discordConfig, ApplicationEventPublisher publisher) {
        this.botName = botName;
        this.discordConfig = discordConfig;
        this.publisher = publisher;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Optional<BbReceiveMessage> receiveMessage = DiscordMessageUtil.formatBbReceiveMessage(event, discordConfig);
        receiveMessage.ifPresent(message -> {
            log.info("收到discord消息，botName：{}，messageId：{}", botName, message.getMessageId());
            publisher.publishEvent(message);
        });
    }
}
