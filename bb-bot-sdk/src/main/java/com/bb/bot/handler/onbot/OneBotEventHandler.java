package com.bb.bot.handler.onbot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.config.BotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import com.bb.bot.handler.BotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OneBot机器人事件分发处理者
 * @author ren
 */
@Slf4j
@ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.ONEBOT)
public class OneBotEventHandler implements BotEventHandler {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private BotConfig botConfig;

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
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        if ("message".equals(jsonObject.getString("post_type"))) {
            //如果类型是消息，封装为公共消息对象
            ReceiveMessage receiveMessage = JSON.parseObject(s, ReceiveMessage.class);
            if ("private".equals(receiveMessage.getMessageType())) {
                bbReceiveMessage.setMessageType(MessageType.PRIVATE);
            }else if ("group".equals(receiveMessage.getMessageType())) {
                bbReceiveMessage.setMessageType(MessageType.GROUP);
                bbReceiveMessage.setGroupId(receiveMessage.getGroupId());
            }
            bbReceiveMessage.setUserId(receiveMessage.getUserId());
            bbReceiveMessage.setMessageId(receiveMessage.getMessageId() == null ? UUID.randomUUID().toString() : receiveMessage.getMessageId().toString());

            //消息文本内容
            StringBuilder messageTextContent = new StringBuilder();
            //@用户对象
            List<MessageUser> atUserList = new ArrayList<>();
            //"message":[{"data":{"qq":"2219486972"},"type":"at"},{"data":{"text":"图"},"type":"text"}]
            JSONArray messageList = JSON.parseArray(receiveMessage.getMessage());
            for (Object message : messageList) {
                JSONObject detail = (JSONObject) message;
                if ("at".equals(detail.getString("type"))) {
                    //如果是@消息，封装为MessageUser对象
                    String qq = detail.getJSONObject("data").getString("qq");
                    atUserList.add(new MessageUser(qq, botConfig.getQq().equals(qq)));
                }else {
                    messageTextContent.append(detail.getJSONObject("data").getString("text"));
                }
            }
            bbReceiveMessage.setMessage(messageTextContent.toString());
            bbReceiveMessage.setAtUserList(atUserList);

            //通过spring事件机制发布消息
            publisher.publishEvent(bbReceiveMessage);
        }

    }

    @Override
    public void closeConnect() {

    }
}
