package com.bb.bot.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.entity.bb.BbAuthMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * bb机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class BbWebSocketClient extends WebSocketClient {

    private final String name;

    /**
     * 连接认证用的appId和密钥
     */
    private final String appId;
    private final String secret;

    /**
     * 客户端消息处理者
     */
    private final BbClientMessageHandler bbClientMessageHandler;

    /**
     * 连接线程间隔
     */
    private final long connectInterval; // 30 seconds

    private Thread connectThread;

    /**
     * 认证通过标识
     */
    private Boolean authPassFlag = false;

    /**
     * 构造方法
     *
     * @param name, connectInterval, serverUri
     */
    public BbWebSocketClient(String name, String appId, String secret,
                             long connectInterval,
                             URI serverUri,
                             BbClientMessageHandler bbClientMessageHandler) {
        super(serverUri);
        this.name = name;
        this.appId = appId;
        this.secret = secret;
        this.connectInterval = connectInterval;
        this.bbClientMessageHandler = bbClientMessageHandler;
        log.info("【" + name + "】WebSocket客户端初始化:" + serverUri.toString());
        connect();
        startConnectThread();
    }

    /**
     * 打开连接时的方法
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("【" + name + "】WebSocket客户端连接成功");
        //发送认证消息
        BbAuthMessage bbAuthMessage = new BbAuthMessage();
        bbAuthMessage.setAppId(appId);
        bbAuthMessage.setSecret(secret);
        this.send(JSON.toJSONString(bbAuthMessage));
    }

    /**
     * 收到消息时
     *
     * @param s
     */
    @Override
    public void onMessage(String s) {
        if (!authPassFlag) {
            JSONObject authResponse = JSON.parseObject(s);
            if (authResponse.getInteger("code") == 200) {
                authPassFlag = true;
            }else {
                log.info("【" + name + "】WebSocket客户端与服务端认证失败:" + authResponse.get("message"));
                authPassFlag = false;
                this.close();
            }
            return;
        }

        BbSocketServerMessage message = JSON.parseObject(s, BbSocketServerMessage.class);
        //调用机器人事件处理者分发接收到的消息
        bbClientMessageHandler.handleMessage(this, message);
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
                    Thread.sleep(connectInterval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connectThread.start();
    }

}
