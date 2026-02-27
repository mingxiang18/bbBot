package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * qq群组消息对象
 * 
 * @author renyuming
 * @since 2026-02-27
 */
@Data
public class QqGroupMessage {
    private static final long serialVersionUID = 1L;

    //消息 id
    private String id;

    //群聊的 openid
    @JSONField(name = "group_openid")
    private String groupOpenId;

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
     * @since 2026-02-27
     */
    @Data
    public static class Author {
        private static final long serialVersionUID = 1L;

        //用户在本群的 member_openid
        @JSONField(name = "member_openid")
        private String memberOpenId;

    }
}
