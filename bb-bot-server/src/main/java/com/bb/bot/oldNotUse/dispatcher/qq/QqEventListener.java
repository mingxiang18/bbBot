package com.bb.bot.oldNotUse.dispatcher.qq;

import com.bb.bot.oldNotUse.api.qq.QqMessageApi;
import com.bb.bot.entity.qq.QqMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 机器人事件监听者
 * @author ren
 */
@Component
public class QqEventListener {

    @Autowired
    private QqEventDispatcher qqEventDispatcher;

    @Autowired
    private QqMessageApi qqMessageApi;

    @EventListener
    public void listenMessageEvent(QqMessage event) {
        //执行消息事件分发
        qqEventDispatcher.handleMessage(event);
    }

}
