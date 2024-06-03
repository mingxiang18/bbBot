package com.bb.bot.oldNotUse.api.qqnt;

import com.bb.bot.entity.qqnt.Peer;
import com.bb.bot.entity.qqnt.SendMessageElement;

import java.util.List;

/**
 * 发起动作请求Api
 * @author ren
 */
public interface MessageApi {

    /**
     * 发送消息(文本)
     */
    public boolean sendMessage(Peer peer, String message);

    /**
     * 发送消息
     */
    public boolean sendMessage(Peer peer, SendMessageElement messageElement);

    /**
     * 发送消息
     */
    public boolean sendMessage(Peer peer, List<SendMessageElement> messageElementList);
}
