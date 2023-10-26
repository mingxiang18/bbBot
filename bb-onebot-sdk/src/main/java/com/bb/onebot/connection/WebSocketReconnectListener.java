package com.bb.onebot.connection;

import com.bb.onebot.event.WebSocketConnectEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * WebSocket服务器断线重连监听者
 * @author ren
 */
@Slf4j
@Component
public class WebSocketReconnectListener {

    /**
     * socket连接地址
     */
    @Value("${onebot.socket.reconnectTime}")
    private Integer reconnectTime;

    @Autowired
    private OneBotWebSocketClient oneBotWebSocketClient;

    @Async("eventDispatcherExecutor")
    @EventListener
    public void listenEvent(WebSocketConnectEvent event) {
        if (!event.connectionStatus()) {
            log.error("机器人WebSocket客户端等待" + reconnectTime + "毫秒后开始重连");
            try {
                Thread.sleep(reconnectTime);
                oneBotWebSocketClient.reconnect();
            }catch (Exception e) {
                log.error("机器人WebSocket客户端重连异常", e);
            }
        }
    }
}
