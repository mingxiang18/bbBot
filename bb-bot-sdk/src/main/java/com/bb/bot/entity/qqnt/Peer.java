package com.bb.bot.entity.qqnt;

import lombok.Data;

/**
 * 消息发送者对象
 * @author ren
 */
@Data
public class Peer {

    /**
     * 消息类型
     * friend-私人，group-群组
     */
    private String chatType;

    private String uid;

    private String name;
}
