package com.bb.bot.config;

import com.bb.bot.connection.BotWebSocketClient;
import com.bb.bot.connection.BotWebSocketServer;
import com.bb.bot.constant.BotType;
import com.bb.bot.handler.qq.QqApiCaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * WebSocket客户端配置类
 */
@Slf4j
@Configuration
public class WebSocketClientConfig {

    /**
     * socket连接地址
     */
    @Value("${webSocket.socket.url}")
    private String webSocketUri;

    @Value("${webSocket.server.port:8888}")
    private Integer serverPort;

    @Autowired
    private QqApiCaller qqApiCaller;

    /**
     * 注入Socket客户端
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.ONEBOT)
    public BotWebSocketClient initWebSocketClient(){
        URI uri = null;
        try {
            uri = new URI(webSocketUri);
        } catch (URISyntaxException e) {
            log.error("获取bot的websocket的URI失败", e);
        }
        BotWebSocketClient webSocketClient = new BotWebSocketClient(uri);
        //启动时创建客户端连接
        webSocketClient.connect();
        return webSocketClient;
    }

    /**
     * 注入Socket服务端
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.QQNT)
    public BotWebSocketServer initWebSocketServer(){
        BotWebSocketServer server = new BotWebSocketServer(serverPort);
        server.start();
        log.info("服务器端启动，端口号为：" + server.getPort());
        return server;
    }

    /**
     * 注入Socket客户端
     * @return
     */
    @Bean
    @ConditionalOnProperty(prefix = "bot", name = "type", havingValue = BotType.QQ)
    public BotWebSocketClient initQqWebSocketClient(){
        URI uri = URI.create(qqApiCaller.getWebSocketUrl());
        BotWebSocketClient webSocketClient = new BotWebSocketClient(uri);
        //启动时创建客户端连接
        webSocketClient.connect();
        return webSocketClient;
    }
}
