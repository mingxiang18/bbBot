package com.bb.bot.oldNotUse.event.oneBot;

import com.bb.bot.entity.oneBot.ReceiveMessage;
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
