package com.bb.bot.api.discord;

import com.bb.bot.api.AbstractMessageStreamSession;
import com.bb.bot.api.FallbackMessageStreamSession;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.config.DiscordConfig;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
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
        sendFileMessages(channel, bbSendMessage.getMessageList());
    }

    public MessageStreamSession startStream(BbSendMessage bbSendMessage) {
        DiscordConfig discordConfig = (DiscordConfig) bbSendMessage.getConfig();
        if (discordConfig == null || discordConfig.getJda() == null) {
            log.warn("discord 流式回复未初始化，降级 sendMessage");
            return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
        }
        MessageChannel channel = getMessageChannel(discordConfig, bbSendMessage);
        if (channel == null) {
            return new FallbackMessageStreamSession(bbSendMessage, this::sendMessage);
        }
        return new DiscordStreamSession(channel);
    }

    /**
     * Discord 流式呈现：首次 sendMessage().complete() 拿到 Message id，
     * 后续 editMessageById 覆盖；超过 2000 字符上限时新开一条消息接着写。
     */
    private static class DiscordStreamSession extends AbstractMessageStreamSession {
        private final MessageChannel channel;
        private String messageId;
        /** Discord 单条消息字符上限 2000，预留 10 字符做截断标记。 */
        private static final int CHUNK_LIMIT = 1990;

        DiscordStreamSession(MessageChannel channel) {
            this.channel = channel;
            // Discord rate limit: 5 edits / 5s per message，节流 1.2s + 80 字符
            this.minFlushIntervalMs = 1200L;
            this.minFlushChars = 80;
        }

        @Override
        protected void flush(boolean isFinal) {
            String text = buffer.toString();
            if (text.isEmpty()) {
                return;
            }
            // 超出单条上限：把当前消息收尾，重置缓冲开启新消息
            if (text.length() > CHUNK_LIMIT) {
                String head = text.substring(0, CHUNK_LIMIT);
                if (messageId == null) {
                    Message sent = channel.sendMessage(head)
                            .setAllowedMentions(Collections.emptyList())
                            .complete();
                    messageId = sent.getId();
                } else {
                    channel.editMessageById(messageId, head)
                            .queue(null, e -> log.warn("discord edit 失败（流式非致命）", e));
                }
                // 余下文本作为新消息开始
                buffer.setLength(0);
                buffer.append(text.substring(CHUNK_LIMIT));
                pendingChunk.setLength(0);
                messageId = null;
                return;
            }
            try {
                if (messageId == null) {
                    Message sent = channel.sendMessage(text)
                            .setAllowedMentions(Collections.emptyList())
                            .complete();
                    messageId = sent.getId();
                } else {
                    channel.editMessageById(messageId, text)
                            .queue(null, e -> log.warn("discord edit 失败（流式非致命）", e));
                }
                pendingChunk.setLength(0);
            } catch (Exception e) {
                log.warn("discord 流式 flush 异常", e);
            }
        }
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

    /** 本地图片 / 本地文件都用 JDA sendFiles 上传（Discord 不区分图片与普通附件）。 */
    private void sendFileMessages(MessageChannel channel, List<BbMessageContent> messageList) {
        for (BbMessageContent bbMessageContent : messageList) {
            String type = bbMessageContent.getType();
            if (!BbSendMessageType.LOCAL_IMAGE.equals(type) && !BbSendMessageType.LOCAL_FILE.equals(type)) {
                continue;
            }
            if (!(bbMessageContent.getData() instanceof File)) {
                log.error("discord本地附件发送失败，消息内容不是File类型：{}", bbMessageContent.getData());
                continue;
            }
            File file = (File) bbMessageContent.getData();
            if (!file.exists() || !file.isFile()) {
                log.error("discord本地附件发送失败，文件不存在：{}", file.getAbsolutePath());
                continue;
            }
            // 优先用消息里指定的展示文件名，否则用文件本身的名字
            String displayName = StringUtils.isNotBlank(bbMessageContent.getFileName())
                    ? bbMessageContent.getFileName() : file.getName();
            channel.sendFiles(FileUpload.fromData(file, displayName))
                    .queue(null, e -> log.error("discord本地附件发送失败，channelId：{}，file：{}",
                            channel.getId(), file.getAbsolutePath(), e));
        }
    }
}
