package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * qq Webhook鉴权响应
 * 
 * @author renyuming
 * @since 2026-02-27
 */
@Data
public class QqWebhookAuthResponse {
    private static final long serialVersionUID = 1L;

    @JSONField(name = "plain_token")
    private String plain_token;

    private String signature;

    public QqWebhookAuthResponse(String plainToken, String signature) {
        this.plain_token = plainToken;
        this.signature = signature;
    }
}
