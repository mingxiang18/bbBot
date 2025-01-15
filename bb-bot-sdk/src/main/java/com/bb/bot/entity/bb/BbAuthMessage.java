package com.bb.bot.entity.bb;

import lombok.Data;

/**
 * Bb连接认证实体
 */
@Data
public class BbAuthMessage {
    /**
     * 服务端身份验证密钥
     */
    private String appId;
    private String secret;
}
