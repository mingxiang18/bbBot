package com.bb.bot.api.telegram;

import com.bb.bot.config.TelegramConfig;
import com.bb.bot.connection.telegram.TelegramApiCaller;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;

/**
 * telegram消息发送Api
 */
@Component
public class TelegramMessageApi {

    @Autowired
    private TelegramApiCaller telegramApiCaller;

    public void sendMessage(BbSendMessage bbSendMessage) {
        if (CollectionUtils.isEmpty(bbSendMessage.getMessageList())) {
            return;
        }

        TelegramConfig telegramConfig = (TelegramConfig) bbSendMessage.getConfig();
        String chatId = getChatId(bbSendMessage);
        if (StringUtils.isBlank(chatId)) {
            return;
        }

        StringBuilder textContent = new StringBuilder();
        boolean replyUsed = false;
        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            if (BbSendMessageType.TEXT.equals(bbMessageContent.getType())) {
                textContent.append(bbMessageContent.getData());
            }else if (BbSendMessageType.AT.equals(bbMessageContent.getType())) {
                appendAtText(textContent, bbMessageContent.getData());
            }
        }

        if (StringUtils.isNoneBlank(textContent.toString())) {
            telegramApiCaller.sendMessage(telegramConfig, chatId, textContent.toString(), bbSendMessage.getReceiveMessageId());
            replyUsed = true;
        }

        for (BbMessageContent bbMessageContent : bbSendMessage.getMessageList()) {
            String replyMessageId = replyUsed ? null : bbSendMessage.getReceiveMessageId();
            if (BbSendMessageType.LOCAL_IMAGE.equals(bbMessageContent.getType())) {
                telegramApiCaller.sendPhoto(telegramConfig, chatId, (File) bbMessageContent.getData(), null, replyMessageId);
                replyUsed = true;
            }else if (BbSendMessageType.NET_IMAGE.equals(bbMessageContent.getType())) {
                telegramApiCaller.sendPhoto(telegramConfig, chatId, bbMessageContent.getData().toString(), null, replyMessageId);
                replyUsed = true;
            }
        }
    }

    private String getChatId(BbSendMessage bbSendMessage) {
        if (MessageType.GROUP.equals(bbSendMessage.getMessageType()) || MessageType.CHANNEL.equals(bbSendMessage.getMessageType())) {
            return bbSendMessage.getGroupId();
        }
        return bbSendMessage.getUserId();
    }

    private void appendAtText(StringBuilder textContent, Object data) {
        if (data == null || StringUtils.isBlank(data.toString())) {
            return;
        }
        String atText = data.toString();
        if (!atText.startsWith("@")) {
            atText = "@" + atText;
        }
        textContent.append(atText).append(" ");
    }
}
