package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram webhook update对象
 */
@Data
public class TelegramUpdate {

    @JSONField(name = "update_id")
    private Long updateId;

    private TelegramMessage message;

    @JSONField(name = "channel_post")
    private TelegramMessage channelPost;
}
