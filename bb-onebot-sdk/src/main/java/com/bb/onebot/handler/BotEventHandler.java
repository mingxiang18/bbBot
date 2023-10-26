package com.bb.onebot.handler;

import org.springframework.scheduling.annotation.Async;

/**
 * 公共机器人事件处理者
 * 抽象一个接口出来调用的原因是，不清楚go-cqhttp的消息和其他类型qq客户端的消息是否会有不一致，如果不一致，可随时替换实现类
 * @author ren
 */
public interface BotEventHandler {

    @Async("eventDispatcherExecutor")
    public void handleMessage(String message);
}
