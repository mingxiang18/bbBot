package com.bb.bot.oldNotUse.dispatcher.oneBot;

import com.bb.bot.oldNotUse.api.oneBot.ActionApi;
import com.bb.bot.oldNotUse.event.oneBot.ReceiveMessageEvent;
import com.bb.bot.oldNotUse.event.oneBot.ReceiveNoticeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class OneBotEventListener {

    @Autowired
    private OneBotEventDispatcher oneBotEventDispatcher;

    @Autowired
    private ActionApi actionApi;

    @EventListener
    public void listenMessageEvent(ReceiveMessageEvent event) {
        //执行消息事件分发
        oneBotEventDispatcher.handleMessage(event);
    }

    @EventListener
    public void listenNoticeEvent(ReceiveNoticeEvent event) {
        //执行提醒事件分发
        oneBotEventDispatcher.handleNotice(event);
    }

}
