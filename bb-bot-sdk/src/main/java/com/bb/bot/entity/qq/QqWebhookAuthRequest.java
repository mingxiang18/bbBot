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
public class QqWebhookAuthRequest {
    private static final long serialVersionUID = 1L;

    @JSONField(name = "plain_token")
    private String plainToken;

    @JSONField(name = "event_ts")
    private String eventTs;
}
