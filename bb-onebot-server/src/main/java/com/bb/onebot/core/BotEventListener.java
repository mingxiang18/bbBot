package com.bb.onebot.core;

import com.bb.onebot.api.ActionApi;
import com.bb.onebot.core.BotEventCoreHandler;
import com.bb.onebot.event.ReceiveMessageEvent;
import com.bb.onebot.event.ReceiveNoticeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class BotEventListener {

    @Autowired
    private BotEventCoreHandler botEventCoreHandler;

    @Autowired
    private ActionApi actionApi;

    @EventListener
    public void listenMessageEvent(ReceiveMessageEvent event) {
        //执行消息事件处理
        botEventCoreHandler.handleMessage(event);
    }

    @EventListener
    public void listenNoticeEvent(ReceiveNoticeEvent event) {
        //执行提醒事件处理
        botEventCoreHandler.handleNotice(event);
    }

}
