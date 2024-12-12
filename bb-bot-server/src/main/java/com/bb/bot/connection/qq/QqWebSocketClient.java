package com.bb.bot.connection.qq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.config.QqConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.entity.qq.SocketMessageEntity;
import com.bb.bot.common.util.LocalCacheUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * onebot机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class QqWebSocketClient extends WebSocketClient {
    @Getter
    private final String name;
    @Getter
    private final QqConfig qqConfig;
    private final ApplicationEventPublisher publisher;
    private final QqApiCaller qqApiCaller;

    /**
     * 连接检查子线程
     */
    private static final long CONNECT_INTERVAL = 30 * 1000; // 30 seconds
    private Thread connectThread;

    /**
     * 用于@的cq码正则
     */
    public final static String AT_COMPILE_REG = "<@.*?>\\s?";

    /**
     * 构造方法
     *
     * @param serverUri
     */
    public QqWebSocketClient(String name, QqConfig qqConfig, ApplicationEventPublisher publisher, QqApiCaller qqApiCaller, URI serverUri) {
        super(serverUri);
        this.name = name;
        this.qqConfig = qqConfig;
        this.publisher = publisher;
        this.qqApiCaller = qqApiCaller;
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
        SocketMessageEntity message = JSON.parseObject(s, SocketMessageEntity.class);

        if (10 == message.getOp()) {
            log.info("【" + name + "】WebSocket客户端接收到hello消息: " + s);
            //如果收到10 hello消息，进行登录鉴权
            Map<String, Object> request = new HashMap<>();
            request.put("token", qqApiCaller.getToken(qqConfig));
            request.put("intents", 1 << 30);
            //没有分片，传默认值
            request.put("shard", new Integer[]{0, 1});

            //封装鉴权消息
            SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
            socketMessageEntity.setOp(2);
            socketMessageEntity.setD(request);

            //发送鉴权消息
            String sendMessage = JSON.toJSONString(socketMessageEntity);
            log.info("【" + name + "】WebSocket客户端发送鉴权消息: " + sendMessage);
            this.send(sendMessage);
        }else if (0 == message.getOp() && "READY".equals(message.getT())) {
            log.info("【" + name + "】WebSocket客户端接收到鉴权答复消息: " + s);
            //收到鉴权答复
            LocalCacheUtils.setCacheObject("qq.session_id", ((JSONObject) message.getD()).getString("session_id"));

            //封装心跳消息
            SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
            socketMessageEntity.setOp(1);
            socketMessageEntity.setD(null);
            //发送心跳消息
            String sendMessage = JSON.toJSONString(socketMessageEntity);
            log.info("【" + name + "】WebSocket客户端发送心跳消息: " + sendMessage);
            this.send(sendMessage);
        }else if(11 == message.getOp()){
            //心跳答复，可以不用管

        }else if(7 == message.getOp()){
            //服务端通知客户端重新连接
            log.info("【" + name + "】WebSocket客户端接收到重新连接通知: " + s);
            //删除缓存
            LocalCacheUtils.removeCacheObject("qq.seq");
            LocalCacheUtils.removeCacheObject("qq.session_id");
            //重新连接
            this.reconnect();
        }else {
            log.info("【" + name + "】WebSocket客户端接收到消息通知: " + s);
            //设置最新消息序号
            LocalCacheUtils.setCacheObject("qq.seq", message.getS());

            //将qq消息Json转为实体
            QqMessage qqMessage = JSON.parseObject(JSON.toJSONString(message.getD()), QqMessage.class);
            //封装为bb协议的消息实体
            BbReceiveMessage bbReceiveMessage = new BbReceiveMessage();
            bbReceiveMessage.setBotType(BotType.QQ);
            bbReceiveMessage.setWebSocket(this);
            bbReceiveMessage.setMessageType(MessageType.GROUP);
            bbReceiveMessage.setUserId(qqMessage.getAuthor().getId());
            bbReceiveMessage.setSender(new MessageUser(qqMessage.getAuthor().getId(), qqMessage.getAuthor().getUsername()));
            bbReceiveMessage.setGroupId(qqMessage.getChannelId());
            bbReceiveMessage.setMessageId(qqMessage.getId());
            //设置消息内容，去掉@的cq码
            bbReceiveMessage.setMessage(qqMessage.getContent().replaceAll(AT_COMPILE_REG, ""));
            if (!CollectionUtils.isEmpty(qqMessage.getMentions())) {
                //封装at用户对象列表
                bbReceiveMessage.setAtUserList(qqMessage.getMentions().stream().map(qqUser -> {
                    MessageUser messageUser = new MessageUser();
                    messageUser.setUserId(qqUser.getId());
                    messageUser.setNickname(qqUser.getUsername());
                    if (qqUser.getBot()) {
                        messageUser.setBotFlag(true);
                    }
                    return messageUser;
                }).collect(Collectors.toList()));
            }

            //通过spring事件机制发布消息
            publisher.publishEvent(bbReceiveMessage);
        }

    }

    /**
     * 子线程定时连接检查
     */
    private void startConnectThread() {
        connectThread = new Thread(() -> {
            while (true) {
                try {
                    if (this.getSocket() == null || this.getSocket().isClosed()) {
                        //如果没有连接则重连
                        reconnect();
                    }else {
                        //如果有连接
                        //封装心跳消息
                        SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
                        socketMessageEntity.setOp(1);
                        socketMessageEntity.setD(LocalCacheUtils.getCacheObject("qq.seq"));

                        //发送心跳消息
                        String sendMessage = JSON.toJSONString(socketMessageEntity);
                        log.debug("【" + name + "】WebSocket客户端发送心跳消息: " + sendMessage);
                        send(sendMessage);
                    }

                } catch (Exception e) {
                    log.error("【" + name + "】WebSocket客户端重连未知异常", e);
                }
                try {
                    Thread.sleep(CONNECT_INTERVAL);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connectThread.start();
    }

}
