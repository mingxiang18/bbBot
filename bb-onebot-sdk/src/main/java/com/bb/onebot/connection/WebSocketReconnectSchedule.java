package com.bb.onebot.connection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WebSocket服务器断线重连监听者
 * @author ren
 */
@Slf4j
@Component
@EnableScheduling
public class WebSocketReconnectSchedule {

    /**
     * socket连接地址
     */
    @Value("${onebot.socket.reconnectTime}")
    private Integer reconnectTime;

    @Autowired
    private OneBotWebSocketClient oneBotWebSocketClient;

    /**
     * 每10s检查一次连接状态
     */
    //@Scheduled(cron = "0/10 * * * * *")
    public void botConnectCheck() {
        if(!oneBotWebSocketClient.hasConnection.get()) {
            log.error("检查到机器人WebSocket客户端未连接，尝试重新连接");
            try {
                oneBotWebSocketClient.reconnect();
            } catch (Exception e) {
                log.error("机器人WebSocket客户端重连异常", e);
            }
        }
    }
}
