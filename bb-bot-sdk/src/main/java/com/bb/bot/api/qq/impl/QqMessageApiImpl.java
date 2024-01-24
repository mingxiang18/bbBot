package com.bb.bot.api.qq.impl;

import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.handler.qq.QqApiCaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 发起动作请求Api实现类
 * @author ren
 */
@Component
public class QqMessageApiImpl implements QqMessageApi {

    @Autowired
    private QqApiCaller qqApiCaller;

    @Override
    public void sendChannelMessage(String channelId, ChannelMessage channelMessage) {
        qqApiCaller.sendChannelMessage(channelId, channelMessage);
    }
}
