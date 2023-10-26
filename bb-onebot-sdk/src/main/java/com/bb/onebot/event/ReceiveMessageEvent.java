package com.bb.onebot.event;

import com.bb.onebot.entity.ReceiveMessage;
import org.springframework.context.ApplicationEvent;


/**
 * 机器人接收消息事件
 * @author ren
 */
public class ReceiveMessageEvent extends ApplicationEvent {
    private ReceiveMessage data;

    public ReceiveMessageEvent(ReceiveMessage source) {
        super(source);
        this.data = source;
    }

    public ReceiveMessage getData() {
        return data;
    }
}
