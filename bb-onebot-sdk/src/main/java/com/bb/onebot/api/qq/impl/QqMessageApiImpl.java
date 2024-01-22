package com.bb.onebot.api.qq.impl;

import com.bb.onebot.api.qq.QqMessageApi;
import com.bb.onebot.entity.qq.SendChannelMessage;
import com.bb.onebot.util.qq.QqUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 发起动作请求Api实现类
 * @author ren
 */
@Component
public class QqMessageApiImpl implements QqMessageApi {

    @Autowired
    private QqUtils qqUtils;

    @Override
    public void sendChannelMessage(String channelId, SendChannelMessage sendChannelMessage) {
        qqUtils.sendChannelMessage(channelId, sendChannelMessage);
    }
}
