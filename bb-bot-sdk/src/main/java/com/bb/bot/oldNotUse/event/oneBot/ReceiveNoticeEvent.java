package com.bb.bot.oldNotUse.event.oneBot;

import com.bb.bot.entity.oneBot.ReceiveNotice;
import org.springframework.context.ApplicationEvent;

/**
 * 机器人接收提醒事件
 * @author ren
 */
public class ReceiveNoticeEvent extends ApplicationEvent {
    private ReceiveNotice data;

    public ReceiveNoticeEvent(ReceiveNotice source) {
        super(source);
        this.data = source;
    }

    public ReceiveNotice getData() {
        return data;
    }
}
