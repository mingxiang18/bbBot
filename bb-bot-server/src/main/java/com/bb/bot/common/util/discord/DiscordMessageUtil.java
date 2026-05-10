package com.bb.bot.common.util.discord;

import com.bb.bot.config.DiscordConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * discord消息转换工具
 */
public class DiscordMessageUtil {

    public static Optional<BbReceiveMessage> formatBbReceiveMessage(MessageReceivedEvent event, DiscordConfig discordConfig) {
        User author = event.getAuthor();
        String botUserId = getBotUserId(event, discordConfig);
        if (author.isBot() || author.getId().equals(botUserId)) {
            return Optional.empty();
        }
        if (!event.isFromGuild() && !discordConfig.isEnableDirectMessage()) {
            return Optional.empty();
        }

        Message message = event.getMessage();
        boolean mentionedBot = message.getMentions().isMentioned(event.getJDA().getSelfUser());
        String senderName = buildSenderName(author, event.getMember());
        return buildBbReceiveMessage(
                author.getId(),
                senderName,
                false,
                message.getId(),
                message.getContentRaw(),
                event.getChannel().getId(),
                !event.isFromGuild(),
                mentionedBot,
                discordConfig
        );
    }

    public static Optional<BbReceiveMessage> buildBbReceiveMessage(String userId, String senderName, boolean senderBot,
                                                                   String messageId, String content, String channelId,
                                                                   boolean directMessage, boolean mentionedBot,
                                                                   DiscordConfig discordConfig) {
        if (senderBot || StringUtils.isBlank(content)) {
            return Optional.empty();
        }

        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.DISCORD);
        bbReceiveMessage.setMessageType(directMessage ? MessageType.PRIVATE : MessageType.CHANNEL);
        bbReceiveMessage.setUserId(userId);
        bbReceiveMessage.setGroupId(channelId);
        bbReceiveMessage.setMessageId(messageId);
        bbReceiveMessage.setSender(new MessageUser(userId, senderName));
        bbReceiveMessage.setMessage(content);
        bbReceiveMessage.setConfig(discordConfig);
        bbReceiveMessage.getMessageContentList().add(BbMessageContent.buildTextContent(content));

        if (mentionedBot) {
            String botUserId = StringUtils.defaultIfBlank(discordConfig.getBotUserId(), "discord-bot");
            bbReceiveMessage.getAtUserList().add(new MessageUser(botUserId, botUserId, true));
        }

        return Optional.of(bbReceiveMessage);
    }

    private static String getBotUserId(MessageReceivedEvent event, DiscordConfig discordConfig) {
        if (StringUtils.isNotBlank(discordConfig.getBotUserId())) {
            return discordConfig.getBotUserId();
        }
        return event.getJDA().getSelfUser().getId();
    }

    private static String buildSenderName(User author, Member member) {
        if (member != null && StringUtils.isNotBlank(member.getEffectiveName())) {
            return member.getEffectiveName();
        }
        if (StringUtils.isNotBlank(author.getGlobalName())) {
            return author.getGlobalName();
        }
        return author.getName();
    }
}
