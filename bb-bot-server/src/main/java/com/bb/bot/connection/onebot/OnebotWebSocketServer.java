package com.bb.bot.connection.onebot;

import com.bb.bot.config.OnebotConfig;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;

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
        //调用机器人事件处理者分发接收到的消息
        OneBotMessageHandler.handleMessage(name, onebotConfig.getQq(), webSocket, s, publisher);
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
}
