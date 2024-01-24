package com.bb.onebot.handler.onbot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.entity.oneBot.ReceiveMessage;
import com.bb.onebot.entity.oneBot.ReceiveNotice;
import com.bb.onebot.event.oneBot.ReceiveMessageEvent;
import com.bb.onebot.event.oneBot.ReceiveNoticeEvent;
import com.bb.onebot.handler.BotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;

/**
 * OneBot机器人事件分发处理者
 * @author ren
 */
@Slf4j
@ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.ONEBOT)
public class OneBotEventHandler implements BotEventHandler {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Override
    @Async("eventDispatcherExecutor")
    public void handleMessage(String s) {
        //将Json转为实体
        JSONObject jsonObject = JSON.parseObject(s);

        //如果事件属于元数据，则不处理
        if ("meta_event".equals(jsonObject.getString("post_type"))) {
            return;
        }

        //如果出现状态消息，打印日志，不处理
        if (jsonObject.containsKey("retcode")) {
            if (!"0".equals(jsonObject.getString("retcode"))) {
                log.error("机器人WebSocket客户端接收到失败状态消息" + s);
            }else {
                log.info("机器人WebSocket客户端接收到成功状态消息" + s);
            }
            return;
        }

        log.info("机器人WebSocket客户端接收到消息" + s);
        if ("message".equals(jsonObject.getString("post_type"))) {
            //如果类型是消息，通过spring事件机制发布消息
            publisher.publishEvent(new ReceiveMessageEvent(JSON.parseObject(s, ReceiveMessage.class)));
        }else if ("notice".equals(jsonObject.getString("post_type"))) {
            //如果类型是提醒，通过spring事件机制发布提醒
            publisher.publishEvent(new ReceiveNoticeEvent(JSON.parseObject(s, ReceiveNotice.class)));
        }

    }

    @Override
    public void closeConnect() {

    }
}
