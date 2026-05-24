package com.bb.bot.entity.bb;

import lombok.Data;

import java.util.List;

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

    /**
     * 客户端能力位（如 stream、file）。服务端据此决定是否下发新协议特性，
     * 缺省（老客户端）按全部不支持处理。
     *
     * @see com.bb.bot.constant.BbCapability
     */
    private List<String> capabilities;
}
