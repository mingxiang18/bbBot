package com.bb.bot.api;

import com.bb.bot.entity.bb.BbSendMessage;

/**
 * 发起动作请求Api
 * @author ren
 */
public interface BbMessageApi {

    /**
     * 发送消息
     */
    public void sendMessage(BbSendMessage bbSendMessage);
}
