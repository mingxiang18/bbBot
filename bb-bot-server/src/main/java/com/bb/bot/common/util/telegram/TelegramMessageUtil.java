package com.bb.bot.common.util.telegram;

import com.bb.bot.config.TelegramConfig;
import com.bb.bot.connection.telegram.TelegramApiCaller;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.telegram.TelegramFile;
import com.bb.bot.entity.telegram.TelegramMessage;
import com.bb.bot.entity.telegram.TelegramPhotoSize;
import com.bb.bot.entity.telegram.TelegramUpdate;
import com.bb.bot.entity.telegram.TelegramUser;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.Optional;

/**
 * telegram消息转换工具
 */
public class TelegramMessageUtil {

    public static BbReceiveMessage formatBbReceiveMessage(TelegramUpdate update, TelegramConfig telegramConfig,
                                                          TelegramApiCaller telegramApiCaller) {
        TelegramMessage telegramMessage = update.getMessage() == null ? update.getChannelPost() : update.getMessage();
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.TELEGRAM);
        bbReceiveMessage.setConfig(telegramConfig);

        String chatType = telegramMessage.getChat().getType();
        if ("private".equals(chatType)) {
            bbReceiveMessage.setMessageType(MessageType.PRIVATE);
        }else if ("group".equals(chatType) || "supergroup".equals(chatType)) {
            bbReceiveMessage.setMessageType(MessageType.GROUP);
            bbReceiveMessage.setGroupId(String.valueOf(telegramMessage.getChat().getId()));
        }else if ("channel".equals(chatType)) {
            bbReceiveMessage.setMessageType(MessageType.CHANNEL);
            bbReceiveMessage.setGroupId(String.valueOf(telegramMessage.getChat().getId()));
        }

        TelegramUser from = telegramMessage.getFrom();
        if (from != null) {
            bbReceiveMessage.setUserId(String.valueOf(from.getId()));
            bbReceiveMessage.setSender(new MessageUser(String.valueOf(from.getId()), buildUserName(from), from.getIsBot()));
        }
        if (StringUtils.isBlank(bbReceiveMessage.getUserId())) {
            bbReceiveMessage.setUserId(String.valueOf(telegramMessage.getChat().getId()));
        }
        bbReceiveMessage.setMessageId(String.valueOf(telegramMessage.getMessageId()));

        String text = StringUtils.defaultIfBlank(telegramMessage.getText(), telegramMessage.getCaption());
        bbReceiveMessage.setMessage(StringUtils.defaultString(text));
        if (StringUtils.isNoneBlank(text)) {
            bbReceiveMessage.getMessageContentList().add(BbMessageContent.buildTextContent(text));
            addAtMeIfNeed(bbReceiveMessage, telegramConfig, text);
        }

        if (telegramMessage.getPhoto() != null && !telegramMessage.getPhoto().isEmpty()) {
            Optional<TelegramPhotoSize> photo = telegramMessage.getPhoto().stream()
                    .max(Comparator.comparing(TelegramMessageUtil::getPhotoSize));
            if (photo.isPresent()) {
                TelegramFile file = telegramApiCaller.getFile(telegramConfig, photo.get().getFileId());
                if (StringUtils.isNoneBlank(file.getFilePath())) {
                    bbReceiveMessage.getMessageContentList()
                            .add(BbMessageContent.buildNetImageMessageContent(telegramApiCaller.buildFileUrl(telegramConfig, file.getFilePath())));
                }
            }
        }

        return bbReceiveMessage;
    }

    private static long getPhotoSize(TelegramPhotoSize photo) {
        if (photo.getFileSize() != null) {
            return photo.getFileSize();
        }
        if (photo.getWidth() != null && photo.getHeight() != null) {
            return (long) photo.getWidth() * photo.getHeight();
        }
        return 0L;
    }

    private static String buildUserName(TelegramUser from) {
        if (StringUtils.isNoneBlank(from.getUsername())) {
            return from.getUsername();
        }
        String firstName = StringUtils.trimToEmpty(from.getFirstName());
        String lastName = StringUtils.trimToEmpty(from.getLastName());
        if (StringUtils.isNoneBlank(lastName)) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    private static void addAtMeIfNeed(BbReceiveMessage bbReceiveMessage, TelegramConfig telegramConfig, String text) {
        if (StringUtils.isBlank(telegramConfig.getBotUsername())) {
            return;
        }
        String atMe = "@" + telegramConfig.getBotUsername();
        if (text.contains(atMe)) {
            bbReceiveMessage.getAtUserList().add(new MessageUser(telegramConfig.getBotUsername(), telegramConfig.getBotUsername(), true));
        }
    }
}
