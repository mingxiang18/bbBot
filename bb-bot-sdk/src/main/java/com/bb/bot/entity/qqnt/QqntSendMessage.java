package com.bb.bot.entity.qqnt;

import lombok.Data;

import java.util.List;

/**
 * 发送的消息
 * @author ren
 */
@Data
public class QqntSendMessage {
    private Peer peer;

    private List<SendMessageElement> messageElementList;
}
