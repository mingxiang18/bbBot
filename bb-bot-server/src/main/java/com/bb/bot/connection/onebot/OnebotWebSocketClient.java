package com.bb.bot.connection.onebot;

import com.bb.bot.config.OnebotConfig;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;

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
        OneBotMessageHandler.handleMessage(name, onebotConfig.getQq(), this, s, publisher);
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
     * 子线程定时连接检查
     */
    private void startConnectThread() {
        connectThread = new Thread(() -> {
            while (true) {
                try {
                    if (this.getSocket() == null || this.getSocket().isClosed()) {
                        reconnect();
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
