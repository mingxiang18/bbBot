package com.bb.bot.entity.qqnt;

import lombok.Data;

/**
 * 接收的消息实体
 * @author ren
 */
@Data
public class QqntReceiveMessage {

    /**
     * 消息发送对象（分私人或群组）
     */
    private Peer peer;

    /**
     * 消息发送者对象（具体用户）
     */
    private Sender sender;

    /**
     * 消息原始内容
     */
    private MessageRaw raw;
}
