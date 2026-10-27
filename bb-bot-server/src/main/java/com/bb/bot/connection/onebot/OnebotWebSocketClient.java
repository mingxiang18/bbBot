package com.bb.bot.connection.onebot;

import com.bb.bot.config.OnebotConfig;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;

/** OneBot 正向 WebSocket 客户端：携带认证头连接网关，并维护断线重连。 */
@Slf4j
public class OnebotWebSocketClient extends WebSocketClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 60;
    private static final long RECONNECT_INTERVAL_MS = 5_000;
    private static final long HANDSHAKE_TIMEOUT_MS = 15_000;

    private final String name;
    private final OnebotConfig config;
    private final ApplicationEventPublisher publisher;
    private final Thread connectThread;

    /** 主动关闭后不再重连；由关闭线程和连接线程共同访问。 */
    private volatile boolean stopped;

    public OnebotWebSocketClient(
            String name, OnebotConfig config, ApplicationEventPublisher publisher, URI uri) {
        super(
                uri,
                new Draft_6455(),
                OneBotAuth.headers(config.getAccessToken()),
                CONNECT_TIMEOUT_MS);
        this.name = name;
        this.config = config;
        this.publisher = publisher;
        setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);
        log.info("OneBot {} connecting to {}", name, OneBotAuth.endpoint(uri));

        connectThread = new Thread(this::connectLoop, "onebot-ws-reconnect-" + name);
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        if (stopped) {
            close();
            return;
        }
        config.getSession().bind(this);
        log.info("OneBot {} connected", name);
    }

    @Override
    public void onMessage(String raw) {
        OneBotMessageHandler.handleMessage(name, config, this, raw, publisher);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        config.getSession().disconnected(this);
        log.info("OneBot {} disconnected code={} remote={}", name, code, remote);
    }

    @Override
    public void onError(Exception error) {
        // 异常信息可能带有含 token 的连接地址，只记录异常类型。
        log.warn("OneBot {} transport error {}", name, error.getClass().getSimpleName());
    }

    /** 每次只发起一次连接；握手尚未完成时等待，超时后关闭并进入下一轮重连。 */
    private void connectLoop() {
        boolean initialConnection = true;
        long lastAttemptAt = 0;
        while (!stopped) {
            try {
                if (!isOpen()) {
                    if (initialConnection) {
                        initialConnection = false;
                        lastAttemptAt = System.currentTimeMillis();
                        connect();
                    } else if (isClosed()) {
                        lastAttemptAt = System.currentTimeMillis();
                        reconnect();
                    } else if (System.currentTimeMillis() - lastAttemptAt > HANDSHAKE_TIMEOUT_MS) {
                        // 对端可能接受 TCP 后一直不完成 WebSocket 握手，不能无限等待。
                        closeConnection(1006, "OneBot handshake timed out");
                    }
                }
                Thread.sleep(RECONNECT_INTERVAL_MS);
            } catch (InterruptedException e) {
                if (stopped) {
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("OneBot {} reconnect failed {}", name, e.getClass().getSimpleName());
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    if (stopped) {
                        return;
                    }
                }
            }
        }
    }

    /** 停止重连，释放当前连接的等待请求与业务队列。 */
    public void shutdown() {
        stopped = true;
        connectThread.interrupt();
        config.getSession().disconnected(this);
        close();
        config.getSession().close();
    }
}
