package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * telegram消息对象
 */
@Data
public class TelegramMessage {

    @JSONField(name = "message_id")
    private Long messageId;

    private TelegramUser from;

    private TelegramChat chat;

    private Long date;

    private String text;

    private String caption;

    private List<TelegramPhotoSize> photo;
}
