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
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * onebot机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class OnebotWebSocketClient extends WebSocketClient {

    private final String name;
    private final OnebotConfig onebotConfig;
    private final ApplicationEventPublisher publisher;

    /**
     * 连接线程间隔
     */
    private static final long CONNECT_INTERVAL = 30 * 1000; // 30 seconds
    private Thread connectThread;

    /**
     * 构造方法
     *
     * @param serverUri
     */
    public OnebotWebSocketClient(String name, OnebotConfig onebotConfig, ApplicationEventPublisher publisher, URI serverUri) {
        super(serverUri);
        this.name = name;
        this.onebotConfig = onebotConfig;
        this.publisher = publisher;
        log.info("【" + name + "】WebSocket客户端初始化:" + serverUri.toString());
        connect();
        startConnectThread();
    }

    /**
     * 打开连接时的方法
     *
     * @param serverHandshake
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("【" + name + "】WebSocket客户端连接成功");
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(String s) {
        //调用机器人事件处理者分发接收到的消息
        handleMessage(s);
    }

    /**
     * 当连接关闭时
     *
     * @param i
     * @param s
     * @param b
     */
    @Override
    public void onClose(int i, String s, boolean b) {
        log.info("【" + name + "】WebSocket客户端连接关闭:" + s);
    }

    /**
     * 发生error时
     *
     * @param e
     */
    @Override
    public void onError(Exception e) {
        log.info("【" + name + "】WebSocket客户端出现异常: " + e.getMessage());
    }

    /**
     * 消息处理
     */
    private void handleMessage(String s) {
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
        bbReceiveMessage.setWebSocket(this);
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

    /**
     * 子线程定时连接检查
     */
    private void startConnectThread() {
        connectThread = new Thread(() -> {
            try {
                while (true) {
                    if (this.getSocket() == null || !this.getSocket().isConnected()) {
                        reconnect();
                    }
                    Thread.sleep(CONNECT_INTERVAL);
                }
            } catch (Exception e) {
                log.error("【" + name + "】WebSocket客户端重连未知异常", e);
            }
        });
        connectThread.start();
    }

}
