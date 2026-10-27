package com.bb.bot.connection.onebot;

import com.bb.bot.config.OnebotConfig;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.springframework.context.ApplicationEventPublisher;
import java.net.InetSocketAddress;

@Slf4j
public class OnebotWebSocketServer extends WebSocketServer {
    private final String name;
    private final OnebotConfig config;
    private final ApplicationEventPublisher publisher;

    public OnebotWebSocketServer(String name, OnebotConfig config, ApplicationEventPublisher publisher, int port) {
        super(new InetSocketAddress(port));
        this.name = name; this.config = config; this.publisher = publisher;
        setConnectionLostTimeout(60);
        start();
    }
    @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket socket, Draft draft,
            ClientHandshake request) throws InvalidDataException {
        if (!OneBotAuth.accepts(config.getAccessToken(), request)) throw new InvalidHandshakeException("Unauthorized");
        String role = request.getFieldValue("X-Client-Role");
        if (role != null && !role.isBlank() && !"Universal".equalsIgnoreCase(role))
            throw new InvalidHandshakeException("Universal connection required");
        String self = request.getFieldValue("X-Self-ID");
        if (self != null && !self.isBlank() && config.getQq() != null && !config.getQq().isBlank()
                && !config.getQq().equals(self)) throw new InvalidHandshakeException("Unexpected account");
        return super.onWebsocketHandshakeReceivedAsServer(socket, draft, request);
    }
    @Override public void onOpen(WebSocket socket, ClientHandshake handshake) {
        config.getSession().bind(socket);
        log.info("OneBot {} reverse WebSocket connected", name);
    }
    @Override public void onMessage(WebSocket socket, String raw) {
        OneBotMessageHandler.handleMessage(name, config, socket, raw, publisher);
    }
    @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        config.getSession().disconnected(socket);
        log.info("OneBot {} reverse WebSocket disconnected code={}", name, code);
    }
    @Override public void onError(WebSocket socket, Exception error) {
        log.warn("OneBot {} reverse WebSocket error {}", name, error.getClass().getSimpleName());
    }
    @Override public void onStart() { log.info("OneBot {} listening port={}", name, getPort()); }
    public void shutdown() throws InterruptedException {
        config.getSession().close();
        stop(3000);
    }
}
