package com.bb.onebot.config;

import com.bb.onebot.connection.OneBotWebSocketClient;
import com.bb.onebot.connection.OneBotWebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${onebot.socket.url}")
    private String webSocketUri;

    /**
     * 注入Socket客户端
     * @return
     */
    @Bean
    public OneBotWebSocketClient initWebSocketClient(){
        URI uri = null;
        try {
            uri = new URI(webSocketUri);
        } catch (URISyntaxException e) {
            log.error("获取bot的websocket的URI失败", e);
        }
        OneBotWebSocketClient webSocketClient = new OneBotWebSocketClient(uri);
        //启动时创建客户端连接
        //webSocketClient.connect();
        return webSocketClient;
    }

    /**
     * 注入Socket服务端
     * @return
     */
    @Bean
    public OneBotWebSocketServer initWebSocketServer(){
        OneBotWebSocketServer server = new OneBotWebSocketServer(8888);
        server.start();
        System.out.println("服务器端启动，端口号为：" + server.getPort());
        return server;
    }
}
