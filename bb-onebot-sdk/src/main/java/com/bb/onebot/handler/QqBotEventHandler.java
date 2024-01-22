package com.bb.onebot.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.onebot.connection.BotWebSocketClient;
import com.bb.onebot.entity.qq.QqMessage;
import com.bb.onebot.entity.qq.SocketMessageEntity;
import com.bb.onebot.util.LocalCacheUtils;
import com.bb.onebot.util.SpringUtils;
import com.bb.onebot.util.qq.QqUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;

import java.util.HashMap;
import java.util.Map;

/**
 * QQ官方机器人事件分发处理者
 * @author ren
 */
@Slf4j
@ConditionalOnProperty(prefix = "bot", name = "type", havingValue = "qq")
public class QqBotEventHandler implements BotEventHandler {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private QqUtils qqUtils;

    @Override
    @Async("eventDispatcherExecutor")
    public void handleMessage(String s) {
        log.info("机器人WebSocket客户端接收到消息" + s);
        SocketMessageEntity message = JSON.parseObject(s, SocketMessageEntity.class);

        if (10 == message.getOp()) {
            //如果收到10 hello消息，进行登录鉴权
            Map<String, Object> request = new HashMap<>();
            request.put("token", qqUtils.getToken());
            request.put("intents", 1 << 9);
            //没有分片，传默认值
            request.put("shard", new Integer[]{0, 1});

            //封装鉴权消息
            SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
            socketMessageEntity.setOp(2);
            socketMessageEntity.setD(request);

            //发送鉴权消息
            BotWebSocketClient webSocketClient = SpringUtils.getBean(BotWebSocketClient.class);
            String sendMessage = JSON.toJSONString(socketMessageEntity);
            log.info("发送鉴权消息: " + sendMessage);
            webSocketClient.send(sendMessage);
        }else if (0 == message.getOp() && "READY".equals(message.getT())) {
            //收到鉴权答复
            LocalCacheUtils.setCacheObject("qq.session_id", ((JSONObject) message.getD()).getString("session_id"));

            //封装心跳消息
            SocketMessageEntity socketMessageEntity = new SocketMessageEntity();
            socketMessageEntity.setOp(1);
            socketMessageEntity.setD(null);
            //发送心跳消息
            BotWebSocketClient webSocketClient = SpringUtils.getBean(BotWebSocketClient.class);
            String sendMessage = JSON.toJSONString(socketMessageEntity);
            log.info("发送心跳消息: " + sendMessage);
            webSocketClient.send(sendMessage);
        }else if(11 == message.getOp()){
            //心跳答复，可以不用管

        }else if(7 == message.getOp()){
            //服务端通知客户端重新连接
            //删除缓存
            LocalCacheUtils.removeCacheObject("qq.seq");
            //重新连接
            BotWebSocketClient webSocketClient = SpringUtils.getBean(BotWebSocketClient.class);
            webSocketClient.reconnect();
        }else {
            //设置最新消息序号
            LocalCacheUtils.setCacheObject("qq.seq", message.getS());
            QqMessage qqMessage = JSON.parseObject(JSON.toJSONString(message.getD()), QqMessage.class);
            //通过spring事件机制发布消息
            publisher.publishEvent(qqMessage);
        }



        //通过spring事件机制发布消息
        //publisher.publishEvent(message);
    }

    @Override
    public void closeConnect() {

    }
}
