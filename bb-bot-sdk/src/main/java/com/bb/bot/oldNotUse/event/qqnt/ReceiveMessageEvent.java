package com.bb.bot.oldNotUse.event.qqnt;

import com.bb.bot.entity.qqnt.QqntReceiveMessage;
import org.springframework.context.ApplicationEvent;

/**
 * 机器人接收消息事件
 * @author ren
 */
public class ReceiveMessageEvent extends ApplicationEvent {
    private QqntReceiveMessage data;

    public ReceiveMessageEvent(QqntReceiveMessage source) {
        super(source);
        this.data = source;
    }

    public QqntReceiveMessage getData() {
        return data;
    }
}
