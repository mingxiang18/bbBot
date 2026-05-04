package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram聊天对象
 */
@Data
public class TelegramChat {

    private Long id;

    private String type;

    private String title;

    private String username;

    @JSONField(name = "first_name")
    private String firstName;

    @JSONField(name = "last_name")
    private String lastName;
}
