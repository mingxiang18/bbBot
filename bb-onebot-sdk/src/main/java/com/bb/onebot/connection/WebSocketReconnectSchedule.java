package com.bb.onebot.connection;

import com.alibaba.fastjson2.JSON;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.entity.qq.SocketMessageEntity;
import com.bb.onebot.util.LocalCacheUtils;
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
     * 定时任务时间表达式cron
     */
    @Value("${bot.type}")
    private String botType;

    @Autowired(required = false)
    private BotWebSocketClient botWebSocketClient;

    /**
     * 每30s检查一次连接状态
     */
    @Scheduled(cron = "${bot.socket.cron:0/30 * * * * *}")
    public void botConnectCheck() {
        if(!botWebSocketClient.hasConnection.get()) {
            log.error("检查到机器人WebSocket客户端未连接，尝试重新连接");
            try {
                botWebSocketClient.reconnect();
            } catch (Exception e) {
                log.error("机器人WebSocket客户端重连异常", e);
            }
        }else {
            //如果已连接，发送心跳
            if (BotType.QQ.equals(botType)) {
                //封装心跳消息
                SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
                socketMessageEntity.setOp(1);
                socketMessageEntity.setD(LocalCacheUtils.getCacheObject("qq.seq"));

                //发送心跳消息
                String sendMessage = JSON.toJSONString(socketMessageEntity);
                log.debug("发送心跳消息: " + sendMessage);
                botWebSocketClient.send(sendMessage);
            }
        }
    }
}
