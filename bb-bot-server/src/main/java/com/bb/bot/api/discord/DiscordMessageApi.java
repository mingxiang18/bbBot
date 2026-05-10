package com.bb.bot.api.discord;

import com.bb.bot.config.DiscordConfig;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * discord消息发送Api
 */
@Slf4j
@Component
public class DiscordMessageApi {

    private static final int DISCORD_TEXT_LIMIT = 2000;

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        DiscordConfig discordConfig = (DiscordConfig) bbSendMessage.getConfig();
        if (discordConfig == null || discordConfig.getJda() == null) {
            log.error("discord消息发送失败，JDA客户端未初始化");
            return;
        }

        MessageChannel channel = getMessageChannel(discordConfig, bbSendMessage);
        if (channel == null) {
            return;
        }

        sendTextMessages(channel, collectTextContent(bbSendMessage));
        sendImageMessages(channel, bbSendMessage.getMessageList());
    }

    private MessageChannel getMessageChannel(DiscordConfig discordConfig, BbSendMessage bbSendMessage) {
        String channelId = bbSendMessage.getGroupId();
        if (StringUtils.isNotBlank(channelId)) {
            MessageChannel channel = discordConfig.getJda().getChannelById(MessageChannel.class, channelId);
            if (channel != null) {
                return channel;
            }
        }

        if (StringUtils.isBlank(bbSendMessage.getUserId())) {
            log.error("discord消息发送失败，缺少channelId和userId");
            return null;
        }
        try {
            User user = discordConfig.getJda().retrieveUserById(bbSendMessage.getUserId()).complete();
            return user.openPrivateChannel().complete();
        } catch (Exception e) {
            log.error("discord消息发送失败，无法打开私聊，userId：{}", bbSendMessage.getUserId(), e);
            return null;
        }
    }

    private String collectTextContent(BbSendMessage bbSendMessage) {
        StringBuilder textContent = new StringBuilder();
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.TEXT.equals(bbMessageContent.getType()) && bbMessageContent.getData() != null) {
                textContent.append(bbMessageContent.getData());
            } else if (BbSendMessageType.AT.equals(bbMessageContent.getType()) && bbMessageContent.getData() != null) {
                textContent.append("<@").append(bbMessageContent.getData()).append("> ");
            } else if (BbSendMessageType.NET_IMAGE.equals(bbMessageContent.getType()) && bbMessageContent.getData() != null) {
                textContent.append("\n").append(bbMessageContent.getData());
            }
        }
        return textContent.toString();
    }

    private void sendTextMessages(MessageChannel channel, String textContent) {
        for (String text : splitText(textContent)) {
            channel.sendMessage(text)
                    .setAllowedMentions(Collections.emptyList())
                    .queue(null, e -> log.error("discord文本消息发送失败，channelId：{}", channel.getId(), e));
        }
    }

    private List<String> splitText(String textContent) {
        if (StringUtils.isBlank(textContent)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        String text = textContent;
        while (text.length() > DISCORD_TEXT_LIMIT) {
            result.add(text.substring(0, DISCORD_TEXT_LIMIT));
            text = text.substring(DISCORD_TEXT_LIMIT);
        }
        if (StringUtils.isNotBlank(text)) {
            result.add(text);
        }
        return result;
    }

    private void sendImageMessages(MessageChannel channel, List<BbMessageContent> messageList) {
        for (BbMessageContent bbMessageContent : messageList) {
            if (!BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                continue;
            }
            if (!(bbMessageContent.getData() instanceof File)) {
                log.error("discord本地图片发送失败，消息内容不是File类型：{}", bbMessageContent.getData());
                continue;
            }
            File imageFile = (File) bbMessageContent.getData();
            if (!imageFile.exists() || !imageFile.isFile()) {
                log.error("discord本地图片发送失败，文件不存在：{}", imageFile.getAbsolutePath());
                continue;
            }
            channel.sendFiles(FileUpload.fromData(imageFile))
                    .queue(null, e -> log.error("discord本地图片发送失败，channelId：{}，file：{}",
                            channel.getId(), imageFile.getAbsolutePath(), e));
        }
    }
}
