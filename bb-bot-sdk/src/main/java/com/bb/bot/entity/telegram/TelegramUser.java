package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram用户对象
 */
@Data
public class TelegramUser {

    private Long id;

    @JSONField(name = "is_bot")
    private Boolean isBot;

    @JSONField(name = "first_name")
    private String firstName;

    @JSONField(name = "last_name")
    private String lastName;

    private String username;
}
