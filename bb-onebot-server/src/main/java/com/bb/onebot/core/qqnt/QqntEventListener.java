package com.bb.onebot.core.qqnt;

import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.event.qqnt.ReceiveMessageEvent;
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
    private QqntEventCoreHandler qqntEventCoreHandler;

    @Autowired
    private ActionApi actionApi;

    @EventListener
    public void listenMessageEvent(ReceiveMessageEvent event) {
        //执行消息事件处理
        qqntEventCoreHandler.handleMessage(event);
    }

}
