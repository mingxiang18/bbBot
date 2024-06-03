package com.bb.bot.dispatcher.common;

import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.entity.common.BbReceiveMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class BbEventListener {

    @Autowired
    private BbEventDispatcher bbEventDispatcher;

    @Autowired
    private QqMessageApi qqMessageApi;

    @EventListener
    public void listenMessageEvent(BbReceiveMessage bbReceiveMessage) {
        //执行消息事件分发
        bbEventDispatcher.handleMessage(bbReceiveMessage);
    }

}
