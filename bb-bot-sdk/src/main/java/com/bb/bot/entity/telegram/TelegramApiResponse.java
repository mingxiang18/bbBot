package com.bb.bot.entity.telegram;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * telegram api通用响应
 */
@Data
public class TelegramApiResponse<T> {

    private Boolean ok;

    private T result;

    @JSONField(name = "error_code")
    private Integer errorCode;

    private String description;
}
