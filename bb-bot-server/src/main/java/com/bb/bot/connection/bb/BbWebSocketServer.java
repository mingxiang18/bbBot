package com.bb.bot.connection.bb;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.config.BbConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbAuthMessage;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * bb机器人websocket客户端连接
 * @author ren
 */
@Slf4j
public class BbWebSocketServer extends WebSocketServer {

    private final String name;
    private final BbConfig bbConfig;
    private final ApplicationEventPublisher publisher;
    // 存储认证状态的 Map
    private static final Map<WebSocket, Boolean> authenticatedClients = new ConcurrentHashMap<>();
    // 存储每个连接认证时上报的能力位（stream / file 等）
    private static final Map<WebSocket, Set<String>> clientCapabilities = new ConcurrentHashMap<>();

    /**
     * 查询某连接认证时上报的能力位。老客户端未上报时返回空集合，调用方据此降级。
     */
    public static Set<String> getCapabilities(WebSocket webSocket) {
        return clientCapabilities.getOrDefault(webSocket, Collections.emptySet());
    }

    /**
     * 连接线程间隔
     */
    private static final long CONNECT_INTERVAL = 30 * 1000; // 30 seconds
    private Thread connectThread;

    /**
     * 构造方法
     *
     * @param bbConfig
     */
    public BbWebSocketServer(String name, BbConfig bbConfig, ApplicationEventPublisher publisher) {
        super(new InetSocketAddress(bbConfig.getPort()));
        this.name = name;
        this.bbConfig = bbConfig;
        this.publisher = publisher;
        log.info("【" + name + "】WebSocket服务器初始化:" + bbConfig.getPort());
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
        //认证判断
        if (!authenticatedClients.getOrDefault(webSocket, false)) {
            BbAuthMessage bbAuthMessage = JSON.parseObject(s, BbAuthMessage.class);
            //如果没有认证，进行认证
            if (bbConfig.getAppId().equals(bbAuthMessage.getAppId()) && bbConfig.getSecret().equals(bbAuthMessage.getSecret())) {
                Map<String, Object> failedResponse = new HashMap<>();
                failedResponse.put("code", 200);
                failedResponse.put("message", "认证成功");
                webSocket.send(JSON.toJSONString(failedResponse));
                authenticatedClients.put(webSocket, true);
                clientCapabilities.put(webSocket, bbAuthMessage.getCapabilities() == null
                        ? Collections.emptySet()
                        : new HashSet<>(bbAuthMessage.getCapabilities()));
                log.info("【" + name + "】客户端认证成功，能力位：" + getCapabilities(webSocket));
            }else {
                Map<String, Object> failedResponse = new HashMap<>();
                failedResponse.put("code", 403);
                failedResponse.put("message", "认证失败");
                webSocket.send(JSON.toJSONString(failedResponse));
                //认证不通过则关闭连接
                webSocket.close();
            }

            return;
        }

        //消息处理
        handleMessage(webSocket, s);
    }

    /**
     * 当连接关闭时
     *
     * @param i
     * @param s
     * @param b
     */@Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.info("【" + name + "】WebSocket与客户端"  + webSocket.getRemoteSocketAddress() + "连接关闭:" + s);
        authenticatedClients.remove(webSocket);
        clientCapabilities.remove(webSocket);
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

    /**
     * 消息处理
     */
    private void handleMessage(WebSocket webSocket, String s) {
        //将Json转为实体
        BbReceiveMessage bbReceiveMessage = JSON.parseObject(s, BbReceiveMessage.class);
        bbReceiveMessage.setBotType(BotType.BB);
        bbReceiveMessage.setWebSocket(webSocket);

        //通过spring事件机制发布消息
        publisher.publishEvent(bbReceiveMessage);
    }

}
