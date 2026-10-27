package com.bb.bot.connection.onebot;

import com.bb.bot.config.OnebotConfig;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;
import java.net.URI;

@Slf4j
public class OnebotWebSocketClient extends WebSocketClient {
    private final String name;
    private final OnebotConfig config;
    private final ApplicationEventPublisher publisher;
    private final Thread connectThread;
    private volatile boolean stopped;

    public OnebotWebSocketClient(String name, OnebotConfig config, ApplicationEventPublisher publisher, URI uri) {
        super(uri, new Draft_6455(), OneBotAuth.headers(config.getAccessToken()), 10000);
        this.name = name;
        this.config = config;
        this.publisher = publisher;
        setConnectionLostTimeout(60);
        log.info("OneBot {} connecting to {}", name, OneBotAuth.endpoint(uri));
        connectThread = new Thread(this::connectLoop, "onebot-ws-reconnect-" + name);
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override public void onOpen(ServerHandshake handshake) {
        if (stopped) { close(); return; }
        config.getSession().bind(this);
        log.info("OneBot {} connected", name);
    }
    @Override public void onMessage(String raw) {
        OneBotMessageHandler.handleMessage(name, config, this, raw, publisher);
    }
    @Override public void onClose(int code, String reason, boolean remote) {
        config.getSession().disconnected(this);
        log.info("OneBot {} disconnected code={} remote={}", name, code, remote);
    }
    @Override public void onError(Exception error) {
        log.warn("OneBot {} transport error {}", name, error.getClass().getSimpleName());
    }
    private void connectLoop() {
        boolean initial = true;
        long attemptAt = 0;
        while (!stopped) {
            try {
                if (!isOpen()) {
                    if (initial) { initial = false; attemptAt = System.currentTimeMillis(); connect(); }
                    else if (isClosed()) { attemptAt = System.currentTimeMillis(); reconnect(); }
                    else if (System.currentTimeMillis() - attemptAt > 15000) {
                        // A peer may accept TCP but never finish the WS handshake.
                        closeConnection(1006, "OneBot handshake timed out");
                    }
                }
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                if (stopped) return;
            } catch (RuntimeException e) {
                log.warn("OneBot {} reconnect failed {}", name, e.getClass().getSimpleName());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { if (stopped) return; }
            }
        }
    }
    public void shutdown() {
        stopped = true;
        connectThread.interrupt();
        config.getSession().disconnected(this);
        close();
        config.getSession().close();
    }
}
