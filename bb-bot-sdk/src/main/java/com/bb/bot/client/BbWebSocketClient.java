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

    /**
     * 两次重连尝试之间的最小间隔。即便检测逻辑出现抖动，也保证不会在握手未完成时
     * 反复 {@code reconnect()}，避免底层每次重连新建读/写线程造成线程风暴。
     */
    private static final long MIN_RECONNECT_INTERVAL_MS = 10_000L;

    /**
     * 底层 ping/pong 心跳超时（秒）。超过该时间未收到对端 pong 即判定连接已死并关闭，
     * 用于发现 TCP 半开/对端假死的情况。
     */
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 30;

    /**
     * 上一次发起连接/重连的时间戳，用于配合 {@link #MIN_RECONNECT_INTERVAL_MS} 做退避。
     */
    private volatile long lastConnectAttemptAt = 0L;

    private Thread connectThread;

    /**
     * 认证通过标识。volatile：onMessage 在读线程置位，onClose 在重连/关闭时清零，
     * 跨线程可见性必须保证，否则重连后可能用到过期的认证状态。
     */
    private volatile boolean authPassFlag = false;

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
        //开启底层 ping/pong 心跳检测：对端 TCP 半开/假死(不回 pong)时主动关闭连接，
        //触发 onClose 后由守护线程重连。否则 isOpen() 会一直为 true、永不重连。
        setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);
        //记为首次连接尝试，避免守护线程在握手期间又触发一次 reconnect
        lastConnectAttemptAt = System.currentTimeMillis();
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
            try {
                JSONObject authResponse = JSON.parseObject(s);
                //getInteger 可能为 null（字段缺失/非法报文），用 Integer 比较避免拆箱 NPE
                if (authResponse != null && Integer.valueOf(200).equals(authResponse.getInteger("code"))) {
                    authPassFlag = true;
                } else {
                    Object msg = authResponse == null ? null : authResponse.get("message");
                    log.info("【" + name + "】WebSocket客户端与服务端认证失败:" + msg);
                    authPassFlag = false;
                    this.close();
                }
            } catch (Exception e) {
                //认证响应解析失败：关闭连接由守护线程重连，避免卡在未认证状态
                log.error("【" + name + "】WebSocket客户端解析认证响应异常，关闭重连。原始报文:" + s, e);
                authPassFlag = false;
                this.close();
            }
            return;
        }

        //业务消息：解析与用户回调都不能让异常冒泡到读线程，否则会拖垮整条连接
        BbSocketServerMessage message;
        try {
            message = JSON.parseObject(s, BbSocketServerMessage.class);
        } catch (Exception e) {
            log.error("【" + name + "】WebSocket客户端解析服务端消息异常，丢弃该帧。原始报文:" + s, e);
            return;
        }
        try {
            //调用机器人事件处理者分发接收到的消息
            bbClientMessageHandler.handleMessage(this, message);
        } catch (Throwable t) {
            //隔离第三方处理逻辑的异常，单条消息处理失败不影响连接与后续消息
            log.error("【" + name + "】WebSocket客户端消息处理器执行异常", t);
        }
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
        //保留堆栈，便于定位连接层异常；底层会在必要时关闭连接，交由守护线程重连
        log.error("【" + name + "】WebSocket客户端出现异常", e);
    }

    /**
     * 子线程定时连接检查。
     *
     * <p>这是一个长生命周期的守护线程，必须保证它<strong>永不退出</strong>：底层
     * java-websocket 每次 {@code reconnect()} 都会新建读/写线程，历史上出现过
     * 重连风暴导致 {@code OutOfMemoryError: unable to create new native thread}
     * （属于 {@link Error} 而非 {@link Exception}）或中断异常把本守护线程打挂、
     * 之后再也不重连的问题。因此这里：</p>
     * <ul>
     *   <li>用 {@link Throwable} 兜底，任何异常/错误都只记录、不退出；</li>
     *   <li>{@link InterruptedException} 不再向上抛，仅清除中断标志后继续守护；</li>
     *   <li>用 {@link #shouldReconnect()} + {@link #MIN_RECONNECT_INTERVAL_MS} 退避，
     *       避免握手未完成期间重复 {@code reconnect()} 引发线程风暴。</li>
     * </ul>
     */
    private void startConnectThread() {
        if (connectThread != null && connectThread.isAlive()) {
            return;
        }
        connectThread = new Thread(this::connectLoop, "bb-ws-reconnect-" + name);
        connectThread.setDaemon(true);
        connectThread.start();
    }

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
                Thread.sleep(connectInterval);
            } catch (InterruptedException e) {
                //底层重连会中断相关线程；不能因中断而杀死本守护线程，清除标志后继续
                Thread.interrupted();
            }
        }
    }

    /**
     * 是否需要发起重连。
     *
     * <p>已连接（OPEN）或正在关闭（CLOSING）时不重连；其余状态
     * （NOT_YET_CONNECTED / CLOSED）可能是“正在握手”，用最小间隔兜底，
     * 防止在连接建立期间被反复触发。</p>
     */
    private boolean shouldReconnect() {
        if (isOpen() || isClosing()) {
            return false;
        }
        return System.currentTimeMillis() - lastConnectAttemptAt >= MIN_RECONNECT_INTERVAL_MS;
    }

}
