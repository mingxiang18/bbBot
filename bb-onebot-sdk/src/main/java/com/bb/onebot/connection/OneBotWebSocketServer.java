package com.bb.onebot.connection;

import com.bb.onebot.handler.BotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * onebot机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class OneBotWebSocketServer extends WebSocketServer {

    @Autowired
    private BotEventHandler botEventHandler;

    @Autowired
    private ApplicationEventPublisher publisher;

    /**
     * 线程安全的Boolean -是否受到消息
     */
    public AtomicBoolean hasMessage = new AtomicBoolean(false);

    /**
     * 线程安全的Boolean -是否已经连接
     */
    public AtomicBoolean hasConnection = new AtomicBoolean(false);

    /**
     * 构造方法
     *
     * @param port 端口号
     */
    public OneBotWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        log.info("机器人WebSocket服务器初始化:" + port);
    }

    /**
     * 打开连接时的方法
     */
    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        log.info("机器人WebSocket服务器连接成功");
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(WebSocket webSocket, String s) {
        hasMessage.set(true);
        log.info("接收到消息：" + s);
        //调用机器人事件处理者分发接收到的消息
        botEventHandler.handleMessage(s);
    }

    /**
     * 当连接关闭时
     *
     * @param i
     * @param s
     * @param b
     */@Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        this.hasConnection.set(false);
        this.hasMessage.set(false);
        log.info("机器人WebSocket服务器连接关闭:" + s);
    }

    /**
     * 发生error时
     *
     * @param e
     */
    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.error("机器人WebSocket服务器出现异常", e);
    }

    @Override
    public void onStart() {
    }
}
