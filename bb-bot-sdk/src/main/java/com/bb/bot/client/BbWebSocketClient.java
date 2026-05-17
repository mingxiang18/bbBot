package com.bb.bot.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.entity.bb.BbAuthMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * bb机器人websocket客户端连接。
 *
 * <p>BB 私有协议握手时通过 {@link BbAuthMessage#getCapabilities()} 上报客户端能力位
 * （见 {@code com.bb.bot.constant.BbCapability}）：</p>
 * <ul>
 *   <li>声明 {@code stream}：服务端按 streamId/streamState 帧式真流式下发，
 *       {@link BbSocketServerMessage} 会带 {@code streamId} 与 {@code streamState}
 *       （start/delta/end），由 {@link BbClientMessageHandler} 自行按 streamId 重组呈现；</li>
 *   <li>声明 {@code file}：服务端可下发 localFile / netFile 附件消息；</li>
 *   <li>不声明（用 6 参构造，capabilities 为空）：服务端降级为分段连发，行为与旧版一致。</li>
 * </ul>
 *
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
     * 握手时上报的客户端能力位（stream / file）。空表示不支持新协议特性。
     */
    private final List<String> capabilities;

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
     * 构造方法（不声明任何能力位，服务端走分段连发，兼容旧版行为）。
     */
    public BbWebSocketClient(String name, String appId, String secret,
                             long connectInterval,
                             URI serverUri,
                             BbClientMessageHandler bbClientMessageHandler) {
        this(name, appId, secret, connectInterval, serverUri, bbClientMessageHandler,
                Collections.emptyList());
    }

    /**
     * 构造方法。
     *
     * @param capabilities 握手上报的能力位，见 {@code com.bb.bot.constant.BbCapability}
     *                     （如 {@code List.of(BbCapability.STREAM, BbCapability.FILE)}）
     */
    public BbWebSocketClient(String name, String appId, String secret,
                             long connectInterval,
                             URI serverUri,
                             BbClientMessageHandler bbClientMessageHandler,
                             List<String> capabilities) {
        super(serverUri);
        this.name = name;
        this.appId = appId;
        this.secret = secret;
        this.connectInterval = connectInterval;
        this.bbClientMessageHandler = bbClientMessageHandler;
        this.capabilities = capabilities == null ? Collections.emptyList() : capabilities;
        log.info("【" + name + "】WebSocket客户端初始化:" + serverUri.toString()
                + " capabilities=" + this.capabilities);
        connect();
        startConnectThread();
    }

    /**
     * 打开连接时的方法
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("【" + name + "】WebSocket客户端连接成功");
        //发送认证消息，并上报客户端能力位（stream / file）
        BbAuthMessage bbAuthMessage = new BbAuthMessage();
        bbAuthMessage.setAppId(appId);
        bbAuthMessage.setSecret(secret);
        bbAuthMessage.setCapabilities(capabilities);
        this.send(JSON.toJSONString(bbAuthMessage));
    }

    /**
     * 收到消息时。
     *
     * <p>认证后收到的是 {@link BbSocketServerMessage}。若握手声明了 {@code stream} 能力，
     * 该消息可能是流式帧（带 {@code streamId} 与 {@code streamState}）——由
     * {@link BbClientMessageHandler} 按 streamId 重组；未声明时每条都是完整消息。</p>
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
        //重置认证标识：否则重连后首帧（认证响应）会被当成业务消息错误解析
        authPassFlag = false;
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
