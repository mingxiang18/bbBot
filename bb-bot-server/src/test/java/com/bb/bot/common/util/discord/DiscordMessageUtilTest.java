package com.bb.bot.common.util.discord;

import com.bb.bot.config.DiscordConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordMessageUtilTest {

    @Test
    void buildBbReceiveMessageShouldMapChannelMessage() {
        DiscordConfig config = new DiscordConfig();
        config.setBotUserId("bot-1");

        Optional<BbReceiveMessage> message = DiscordMessageUtil.buildBbReceiveMessage(
                "user-1", "ren", false, "message-1", "hello", "channel-1", false, true, config);

        assertTrue(message.isPresent());
        BbReceiveMessage receiveMessage = message.get();
        assertEquals(BotType.DISCORD, receiveMessage.getBotType());
        assertEquals(MessageType.CHANNEL, receiveMessage.getMessageType());
        assertEquals("user-1", receiveMessage.getUserId());
        assertEquals("channel-1", receiveMessage.getGroupId());
        assertEquals("message-1", receiveMessage.getMessageId());
        assertEquals("hello", receiveMessage.getMessage());
        assertEquals(1, receiveMessage.getMessageContentList().size());
        assertEquals(1, receiveMessage.getAtUserList().size());
        assertTrue(receiveMessage.getAtUserList().get(0).getBotFlag());
    }

    @Test
    void buildBbReceiveMessageShouldMapDirectMessage() {
        DiscordConfig config = new DiscordConfig();

        Optional<BbReceiveMessage> message = DiscordMessageUtil.buildBbReceiveMessage(
                "user-1", "ren", false, "message-1", "hello", "dm-1", true, false, config);

        assertTrue(message.isPresent());
        assertEquals(MessageType.PRIVATE, message.get().getMessageType());
        assertEquals("dm-1", message.get().getGroupId());
        assertTrue(message.get().getAtUserList().isEmpty());
    }

    @Test
    void buildBbReceiveMessageShouldIgnoreBotMessage() {
        DiscordConfig config = new DiscordConfig();

        Optional<BbReceiveMessage> message = DiscordMessageUtil.buildBbReceiveMessage(
                "bot-user", "bot", true, "message-1", "hello", "channel-1", false, false, config);

        assertFalse(message.isPresent());
    }

    @Test
    void buildBbReceiveMessageShouldIgnoreBlankMessage() {
        DiscordConfig config = new DiscordConfig();

        Optional<BbReceiveMessage> message = DiscordMessageUtil.buildBbReceiveMessage(
                "user-1", "ren", false, "message-1", " ", "channel-1", false, false, config);

        assertFalse(message.isPresent());
    }
}
