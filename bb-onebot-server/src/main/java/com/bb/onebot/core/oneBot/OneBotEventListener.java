package com.bb.onebot.core.oneBot;

import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.event.oneBot.ReceiveMessageEvent;
import com.bb.onebot.event.oneBot.ReceiveNoticeEvent;
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
    private OneBotEventCoreHandler oneBotEventCoreHandler;

    @Autowired
    private ActionApi actionApi;

    @EventListener
    public void listenMessageEvent(ReceiveMessageEvent event) {
        //执行消息事件处理
        oneBotEventCoreHandler.handleMessage(event);
    }

    @EventListener
    public void listenNoticeEvent(ReceiveNoticeEvent event) {
        //执行提醒事件处理
        oneBotEventCoreHandler.handleNotice(event);
    }

}
