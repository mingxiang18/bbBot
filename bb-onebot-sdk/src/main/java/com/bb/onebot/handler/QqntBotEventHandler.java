package com.bb.onebot.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.onebot.entity.qqnt.QqntReceiveMessage;
import com.bb.onebot.event.qqnt.ReceiveMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * QQNT机器人事件分发处理者
 * @author ren
 */
@Slf4j
@ConditionalOnProperty(prefix = "bot", name = "type", havingValue = "qqnt")
public class QqntBotEventHandler implements BotEventHandler {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Override
    @Async("eventDispatcherExecutor")
    public void handleMessage(String s) {
        log.info("机器人WebSocket客户端接收到消息" + s);
        List<QqntReceiveMessage> messageList = JSON.parseObject(s, new TypeReference<List<QqntReceiveMessage>>() {});
        //通过spring事件机制发布消息
        publisher.publishEvent(new ReceiveMessageEvent(messageList.get(0)));
    }

    @Override
    public void closeConnect() {

    }
}
