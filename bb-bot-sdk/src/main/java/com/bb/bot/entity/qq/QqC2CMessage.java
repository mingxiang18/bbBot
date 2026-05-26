package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * qq单聊（C2C 普通私聊）消息对象
 *
 * @author renyuming
 * @since 2026-05-26
 */
@Data
public class QqC2CMessage {
    private static final long serialVersionUID = 1L;

    //消息 id
    private String id;

    //消息内容
    private String content;

    //消息创建时间
    private String timestamp;

    //消息创建者
    private Author author;

    /**
     * 用户信息
     *
     * @author renyuming
     * @since 2026-05-26
     */
    @Data
    public static class Author {
        private static final long serialVersionUID = 1L;

        //用户的 user_openid
        @JSONField(name = "user_openid")
        private String userOpenId;
    }
}
