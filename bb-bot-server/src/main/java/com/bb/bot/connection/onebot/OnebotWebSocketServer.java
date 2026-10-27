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

/** OneBot 反向 WebSocket 服务端，供 SnowLuma 等网关主动连接。 */
@Slf4j
public class OnebotWebSocketServer extends WebSocketServer {

    private final String name;
    private final OnebotConfig config;
    private final ApplicationEventPublisher publisher;

    public OnebotWebSocketServer(
            String name, OnebotConfig config, ApplicationEventPublisher publisher, int port) {
        super(new InetSocketAddress(port));
        this.name = name;
        this.config = config;
        this.publisher = publisher;
        setConnectionLostTimeout(60);
        start();
    }

    /** 在绑定会话之前验证 token、连接角色和账号，防止无效连接替换有效连接。 */
    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket socket, Draft draft, ClientHandshake request) throws InvalidDataException {
        if (!OneBotAuth.accepts(config.getAccessToken(), request)) {
            throw new InvalidHandshakeException("Unauthorized");
        }

        // 同一连接既接收事件又发送 API 请求，需要网关提供 Universal 角色。
        String role = request.getFieldValue("X-Client-Role");
        if (role != null && !role.isBlank() && !"Universal".equalsIgnoreCase(role)) {
            throw new InvalidHandshakeException("Universal connection required");
        }

        String selfId = request.getFieldValue("X-Self-ID");
        if (selfId != null
                && !selfId.isBlank()
                && config.getQq() != null
                && !config.getQq().isBlank()
                && !config.getQq().equals(selfId)) {
            throw new InvalidHandshakeException("Unexpected account");
        }
        return super.onWebsocketHandshakeReceivedAsServer(socket, draft, request);
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
        config.getSession().bind(socket);
        log.info("OneBot {} reverse WebSocket connected", name);
    }

    @Override
    public void onMessage(WebSocket socket, String raw) {
        OneBotMessageHandler.handleMessage(name, config, socket, raw, publisher);
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        config.getSession().disconnected(socket);
        log.info("OneBot {} reverse WebSocket disconnected code={}", name, code);
    }

    @Override
    public void onError(WebSocket socket, Exception error) {
        log.warn("OneBot {} reverse WebSocket error {}", name, error.getClass().getSimpleName());
    }

    @Override
    public void onStart() {
        log.info("OneBot {} listening port={}", name, getPort());
    }

    /** 应用退出时先释放等待请求，再停止监听并关闭网关连接。 */
    public void shutdown() throws InterruptedException {
        config.getSession().close();
        stop(3000);
    }
}
