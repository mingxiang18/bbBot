package com.bb.onebot.entity.qqnt;

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
