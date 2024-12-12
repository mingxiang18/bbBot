package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * onebot机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class OnebotWebSocketServer extends WebSocketServer {

    private final String name;
    private final OnebotConfig onebotConfig;
    private final ApplicationEventPublisher publisher;

    /**
     * 构造方法
     *
     * @param port 端口号
     */
    public OnebotWebSocketServer(String name, OnebotConfig onebotConfig, ApplicationEventPublisher publisher, int port) {
        super(new InetSocketAddress(port));
        this.name = name;
        this.onebotConfig = onebotConfig;
        this.publisher = publisher;
        log.info("【" + name + "】WebSocket服务器初始化:" + port);
        this.start();
    }

    /**
     * 打开连接时的方法
     */
    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        log.info("【" + name + "】WebSocket服务器连接到客户端：" + webSocket.getRemoteSocketAddress());
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(WebSocket webSocket, String s) {
        //消息处理
        handleMessage(webSocket, s);
    }

    /**
     * 当连接关闭时
     *
     * @param i
     * @param s
     * @param b
     */@Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.info("【" + name + "】WebSocket服务器连接关闭:" + s);
    }

    /**
     * 发生error时
     *
     * @param e
     */
    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.error("【" + name + "】WebSocket服务器出现异常", e);
    }

    @Override
    public void onStart() {
        log.info("【" + name + "】WebSocket服务器启动成功");
    }

    /**
     * 消息处理
     */
    private void handleMessage(WebSocket webSocket, String s) {
        //将Json转为实体
        JSONObject jsonObject = JSON.parseObject(s);

        //如果事件属于元数据，则不处理
        if ("meta_event".equals(jsonObject.getString("post_type"))) {
            return;
        }

        //如果出现状态消息，打印日志，不处理
        if (jsonObject.containsKey("retcode")) {
            if (!"0".equals(jsonObject.getString("retcode"))) {
                log.error("【" + name + "】WebSocket客户端接收到失败状态消息" + s);
            }else {
                log.info("【" + name + "】WebSocket客户端接收到成功状态消息" + s);
            }
            return;
        }

        log.info("【" + name + "】WebSocket客户端接收到消息" + s);
        BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
        bbReceiveMessage.setBotType(BotType.ONEBOT);
        bbReceiveMessage.setWebSocket(webSocket);
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
            bbReceiveMessage.setSender(new MessageUser(receiveMessage.getSender().getUserId(), receiveMessage.getSender().getNickname()));
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
                    atUserList.add(new MessageUser(qq, onebotConfig.getQq().equals(qq)));
                }else {
                    //如果是文本消息，添加到内容体
                    String text = detail.getJSONObject("data").getString("text");
                    if (StringUtils.isNoneBlank(text)) {
                        messageTextContent.append(text);
                    }
                }
            }
            bbReceiveMessage.setMessage(messageTextContent.toString());
            bbReceiveMessage.setAtUserList(atUserList);

            //通过spring事件机制发布消息
            publisher.publishEvent(bbReceiveMessage);
        }

    }
}