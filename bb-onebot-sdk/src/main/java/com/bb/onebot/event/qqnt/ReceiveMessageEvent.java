package com.bb.onebot.event.qqnt;

import com.bb.onebot.entity.qqnt.QqntReceiveMessage;
import org.springframework.context.ApplicationEvent;

import java.util.List;

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
