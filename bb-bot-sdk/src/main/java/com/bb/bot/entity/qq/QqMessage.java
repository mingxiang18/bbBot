package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * qq消息对象
 * 
 * @author renyuming
 * @date 2024-01-22
 */
@Data
public class QqMessage {
    private static final long serialVersionUID = 1L;

    //消息 id
    private String id;

    //子频道 id
    @JSONField(name = "channel_id")
    private String channelId;

    //频道 id
    @JSONField(name = "guild_id")
    private String guildId;

    //消息内容
    private String content;

    //消息创建时间
    private String timestamp;

    //消息编辑时间
    @JSONField(name = "edited_timestamp")
    private String editedTimestamp;

    //是否是@全员消息
    @JSONField(name = "mention_everyone")
    private Boolean mentionEveryone;

    //消息创建者
    private QqUser author;

    //附件
    //private String attachments;

    //embed
    //private String embeds;

    //消息中@的人
    private List<QqUser> mentions;

    //消息创建者的member信息
    //private String member;

    //ark消息
    private String ark;

    //用于消息间的排序，seq 在同一子频道中按从先到后的顺序递增，不同的子频道之间消息无法排序。(目前只在消息事件中有值，2022年8月1日 后续废弃)
    private Integer seq;

    //子频道消息 seq，用于消息间的排序，seq 在同一子频道中按从先到后的顺序递增，不同的子频道之间消息无法排序
    @JSONField(name = "seq_in_channel")
    private String seqInChannel;

    //引用消息对象
    //private String message_reference;

    /**
     * 用户信息
     *
     * @author renyuming
     * @date 2024-01-22
     */
    @Data
    public static class QqUser {
        private static final long serialVersionUID = 1L;

        //用户 id
        private String id;

        //用户名
        private String username;

        //用户头像地址
        private String avatar;

        //是否是机器人
        private Boolean bot;

        //特殊关联应用的 openid，需要特殊申请并配置后才会返回。如需申请，请联系平台运营人员。
        @JSONField(name = "union_openid")
        private String unionOpenid;

        //机器人关联的互联应用的用户信息，与union_openid关联的应用是同一个。如需申请，请联系平台运营人员。
        @JSONField(name = "unionUserAccount")
        private String union_user_account;

    }
}
