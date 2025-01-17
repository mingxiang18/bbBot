package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.WebSocket;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OneBot协议的消息处理者
 */
@Slf4j
public class OneBotMessageHandler {

    /**
     * 消息处理
     */
    public static void handleMessage(String botName, String botQq,
                                     WebSocket webSocket, String receiveMessageStr, ApplicationEventPublisher publisher) {
        //将Json转为实体
        JSONObject jsonObject = JSON.parseObject(receiveMessageStr);

        //如果事件属于元数据，则不处理
        if ("meta_event".equals(jsonObject.getString("post_type"))) {
            return;
        }

        //如果出现状态消息，打印日志，不处理
        if (jsonObject.containsKey("retcode")) {
            if (!"0".equals(jsonObject.getString("retcode"))) {
                log.error("【" + botName + "】WebSocket客户端接收到失败状态消息" + receiveMessageStr);
            }else {
                log.info("【" + botName + "】WebSocket客户端接收到成功状态消息" + receiveMessageStr);
            }
            return;
        }

        log.info("【" + botName + "】WebSocket客户端接收到消息" + receiveMessageStr);
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.ONEBOT);
        bbReceiveMessage.setWebSocket(webSocket);
        if ("message".equals(jsonObject.getString("post_type"))) {
            //如果类型是消息，封装为公共消息对象
            ReceiveMessage receiveMessage = JSON.parseObject(receiveMessageStr, ReceiveMessage.class);
            if ("private".equals(receiveMessage.getMessageType())) {
                bbReceiveMessage.setMessageType(MessageType.PRIVATE);
            }else if ("group".equals(receiveMessage.getMessageType())) {
                bbReceiveMessage.setMessageType(MessageType.GROUP);
                bbReceiveMessage.setGroupId(receiveMessage.getGroupId());
            }
            bbReceiveMessage.setUserId(receiveMessage.getUserId());
            bbReceiveMessage.setSender(new MessageUser(receiveMessage.getSender().getUserId(), receiveMessage.getSender().getNickname()));
            bbReceiveMessage.setMessageId(receiveMessage.getMessageId() == null ? UUID.randomUUID().toString() : receiveMessage.getMessageId().toString());

            //消息文本内容
            StringBuilder messageTextContent = new StringBuilder();
            //@用户对象
            List<MessageUser> atUserList = new ArrayList<>();
            //bb消息内容体
            List<BbMessageContent> bbMessageContentList = new ArrayList<>();
            //"message":[{"data":{"qq":"2219486972"},"type":"at"},{"data":{"text":"图"},"type":"text"}]
            JSONArray messageList = JSON.parseArray(receiveMessage.getMessage());
            for (Object message : messageList) {
                JSONObject detail = (JSONObject) message;
                if ("at".equals(detail.getString("type"))) {
                    //如果是@消息，封装为MessageUser对象
                    String qq = detail.getJSONObject("data").getString("qq");
                    atUserList.add(new MessageUser(qq, botQq.equals(qq)));
                    //封装为bb消息内容体
                    bbMessageContentList.add(BbMessageContent.buildAtMessageContent(qq));
                }else if ("text".equals(detail.getString("type"))) {
                    //如果是文本消息，添加到内容体
                    String text = detail.getJSONObject("data").getString("text");
                    if (StringUtils.isNoneBlank(text)) {
                        messageTextContent.append(text);
                    }
                    //封装为bb消息内容体
                    bbMessageContentList.add(BbMessageContent.buildTextContent(text));
                }else if ("image".equals(detail.getString("type"))) {
                    //如果是图片消息
                    String url = detail.getJSONObject("data").getString("url");
                    //封装为bb消息内容体
                    bbMessageContentList.add(BbMessageContent.buildNetImageMessageContent(url));
                }else if ("reply".equals(detail.getString("type"))) {
                    //如果是回复消息
                    String messageId = detail.getJSONObject("data").getString("id");
                    //封装为bb消息内容体
                    bbMessageContentList.add(BbMessageContent.buildReplyMessageContent(messageId));
                }
            }
            bbReceiveMessage.setMessage(messageTextContent.toString());
            bbReceiveMessage.setMessageContentList(bbMessageContentList);
            bbReceiveMessage.setAtUserList(atUserList);

            //通过spring事件机制发布消息
            publisher.publishEvent(bbReceiveMessage);
        }
    }
}
