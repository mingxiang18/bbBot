package com.bb.onebot.core.qq;

import com.bb.onebot.api.qq.QqMessageApi;
import com.bb.onebot.entity.qq.QqMessage;
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
    private QqEventCoreHandler qqEventCoreHandler;

    @Autowired
    private QqMessageApi qqMessageApi;

    @EventListener
    public void listenMessageEvent(QqMessage event) {
        //执行消息事件处理
        qqEventCoreHandler.handleMessage(event);
    }

}