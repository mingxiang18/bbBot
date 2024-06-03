package com.bb.bot.oldNotUse.dispatcher.qqnt;

import com.bb.bot.oldNotUse.api.oneBot.ActionApi;
import com.bb.bot.oldNotUse.event.qqnt.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class QqntEventListener {

    @Autowired
    private QqntEventDispatcher qqntEventDispatcher;

    @Autowired
    private ActionApi actionApi;

    @EventListener
    public void listenMessageEvent(ReceiveMessageEvent event) {
        //执行消息事件分发
        qqntEventDispatcher.handleMessage(event);
    }

}
