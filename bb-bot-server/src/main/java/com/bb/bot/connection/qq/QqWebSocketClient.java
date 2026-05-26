package com.bb.bot.connection.qq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.config.QqConfig;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.qq.QqCommonPayloadEntity;
import com.bb.bot.common.util.LocalCacheUtils;
import com.bb.bot.common.util.qq.QQMessageUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.util.*;

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
    /**
     * 两次重连尝试之间的最小间隔，避免握手未完成时反复 reconnect 造成线程风暴。
     */
    private static final long MIN_RECONNECT_INTERVAL_MS = 10_000L;
    /**
     * 上一次发起连接/重连的时间戳，用于重连退避。
     */
    private volatile long lastConnectAttemptAt = 0L;
    private Thread connectThread;

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
        //记为首次连接尝试，避免守护线程在握手期间又触发一次 reconnect
        lastConnectAttemptAt = System.currentTimeMillis();
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
        QqCommonPayloadEntity message = JSON.parseObject(s, QqCommonPayloadEntity.class);

        if (10 == message.getOp()) {
            log.info("【" + name + "】WebSocket客户端接收到hello消息: " + s);
            //如果收到10 hello消息，进行登录鉴权
            Map<String, Object> request = new HashMap<>();
            request.put("token", qqApiCaller.getToken(qqConfig));
            //1<<30 频道@消息，1<<25 群/单聊事件(含C2C私聊)，1<<12 频道私信
            request.put("intents", 1 << 30 | 1 << 25 | 1 << 12);
            //没有分片，传默认值
            request.put("shard", new Integer[]{0, 1});

            //封装鉴权消息
            QqCommonPayloadEntity qqCommonPayloadEntity = new QqCommonPayloadEntity();
            qqCommonPayloadEntity.setOp(2);
            qqCommonPayloadEntity.setD(request);

            //发送鉴权消息
            String sendMessage = JSON.toJSONString(qqCommonPayloadEntity);
            log.info("【" + name + "】WebSocket客户端发送鉴权消息: " + sendMessage);
            this.send(sendMessage);
        }else if (0 == message.getOp() && "READY".equals(message.getT())) {
            log.info("【" + name + "】WebSocket客户端接收到鉴权答复消息: " + s);
            //收到鉴权答复
            LocalCacheUtils.setCacheObject("qq.session_id", ((JSONObject) message.getD()).getString("session_id"));

            //封装心跳消息
            QqCommonPayloadEntity qqCommonPayloadEntity = new QqCommonPayloadEntity();
            qqCommonPayloadEntity.setOp(1);
            qqCommonPayloadEntity.setD(null);
            //发送心跳消息
            String sendMessage = JSON.toJSONString(qqCommonPayloadEntity);
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

            if ("GROUP_AT_MESSAGE_CREATE".equals(message.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromGroup(message, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }else if ("AT_MESSAGE_CREATE".equals(message.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromChannel(message, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }else if ("C2C_MESSAGE_CREATE".equals(message.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromC2C(message, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }else if ("DIRECT_MESSAGE_CREATE".equals(message.getT())) {
                BbReceiveMessage bbReceiveMessage = QQMessageUtil.formatBbReceiveMessageFromDirect(message, qqConfig);
                //通过spring事件机制发布消息
                publisher.publishEvent(bbReceiveMessage);
            }
        }

    }

    /**
     * 子线程定时连接检查
     */
    private void startConnectThread() {
        if (connectThread != null && connectThread.isAlive()) {
            return;
        }
        connectThread = new Thread(this::connectLoop, "qq-ws-reconnect-" + name);
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * 长生命周期守护线程，必须永不退出（与 SDK BbWebSocketClient 同样的兜底逻辑）：
     * 捕获 {@link Throwable} 防止 OOM 等 Error 打挂线程；中断不再向上抛；重连用退避，
     * 避免握手期间反复 {@code reconnect()} 造成线程风暴。已连接时照常发心跳。
     */
    private void connectLoop() {
        while (true) {
            try {
                if (isOpen()) {
                    //已连接：封装并发送心跳消息
                    QqCommonPayloadEntity qqCommonPayloadEntity = new QqCommonPayloadEntity();
                    qqCommonPayloadEntity.setOp(1);
                    qqCommonPayloadEntity.setD(LocalCacheUtils.getCacheObject("qq.seq"));

                    String sendMessage = JSON.toJSONString(qqCommonPayloadEntity);
                    log.debug("【" + name + "】WebSocket客户端发送心跳消息: " + sendMessage);
                    send(sendMessage);
                } else if (shouldReconnect()) {
                    //未连接且已过退避间隔：重连
                    lastConnectAttemptAt = System.currentTimeMillis();
                    log.info("【" + name + "】WebSocket客户端检测到未连接(closing=" + isClosing()
                            + ",closed=" + isClosed() + ")，触发重连");
                    reconnect();
                }
            } catch (Throwable t) {
                //捕获 Throwable（含 OutOfMemoryError 等），守护线程绝不能因此退出
                log.error("【" + name + "】WebSocket客户端重连/心跳异常", t);
            }
            try {
                Thread.sleep(CONNECT_INTERVAL);
            } catch (InterruptedException e) {
                //底层重连会中断相关线程；不能因中断而杀死本守护线程，清除标志后继续
                Thread.interrupted();
            }
        }
    }

    /**
     * 是否需要重连：正在关闭（CLOSING）时不重连，其余未连接状态用最小间隔退避，
     * 防止握手期间被反复触发。
     */
    private boolean shouldReconnect() {
        if (isClosing()) {
            return false;
        }
        return System.currentTimeMillis() - lastConnectAttemptAt >= MIN_RECONNECT_INTERVAL_MS;
    }

}
