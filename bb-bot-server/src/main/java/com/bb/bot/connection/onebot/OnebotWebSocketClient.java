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
    public OnebotWebSocketClient(String name, OnebotConfig onebotConfig, ApplicationEventPublisher publisher, URI serverUri) {
        super(serverUri);
        this.name = name;
        this.onebotConfig = onebotConfig;
        this.publisher = publisher;
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
        if (connectThread != null && connectThread.isAlive()) {
            return;
        }
        connectThread = new Thread(this::connectLoop, "onebot-ws-reconnect-" + name);
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * 长生命周期守护线程，必须永不退出（与 SDK BbWebSocketClient 同样的兜底逻辑）：
     * 捕获 {@link Throwable} 防止 OOM 等 Error 打挂线程；中断不再向上抛；重连用退避，
     * 避免握手期间反复 {@code reconnect()} 造成线程风暴。
     */
    private void connectLoop() {
        while (true) {
            try {
                if (shouldReconnect()) {
                    lastConnectAttemptAt = System.currentTimeMillis();
                    log.info("【" + name + "】WebSocket客户端检测到未连接(open=" + isOpen()
                            + ",closing=" + isClosing() + ",closed=" + isClosed() + ")，触发重连");
                    reconnect();
                }
            } catch (Throwable t) {
                //捕获 Throwable（含 OutOfMemoryError 等），守护线程绝不能因此退出
                log.error("【" + name + "】WebSocket客户端重连检查异常", t);
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
     * 是否需要重连：已连接（OPEN）或正在关闭（CLOSING）时不重连，其余状态用最小间隔退避。
     */
    private boolean shouldReconnect() {
        if (isOpen() || isClosing()) {
            return false;
        }
        return System.currentTimeMillis() - lastConnectAttemptAt >= MIN_RECONNECT_INTERVAL_MS;
    }

}
