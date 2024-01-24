package com.bb.onebot.connection;

import com.bb.onebot.handler.BotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * onebot机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class BotWebSocketClient extends WebSocketClient {

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
     * @param serverUri
     */
    public BotWebSocketClient(URI serverUri) {
        super(serverUri);
        log.info("机器人WebSocket客户端初始化:" + serverUri.toString());
    }

    /**
     * 打开连接时的方法
     *
     * @param serverHandshake
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("机器人WebSocket客户端连接成功");
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(String s) {
        hasMessage.set(true);
        //调用机器人事件处理者分发接收到的消息
        botEventHandler.handleMessage(s);
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
        this.hasConnection.set(false);
        this.hasMessage.set(false);
        log.info("机器人WebSocket客户端连接关闭:" + s);
    }

    /**
     * 发生error时
     *
     * @param e
     */
    @Override
    public void onError(Exception e) {
        log.info("机器人WebSocket客户端出现异常: " + e.getMessage());
    }

    @Override
    public void connect() {
        if(!this.hasConnection.get()){
            super.connect();
            hasConnection.set(true);
        }
    }

    @Override
    public void reconnect() {
        super.reconnect();
        hasConnection.set(true);
    }
}
