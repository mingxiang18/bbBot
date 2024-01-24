package com.bb.bot.api.qq;

import com.bb.bot.entity.qq.ChannelMessage;

/**
 * 发起动作请求Api
 * @author ren
 */
public interface QqMessageApi {
    /**
     * 发送频道消息
     */
    public void sendChannelMessage(String channelId, ChannelMessage channelMessage);
}
