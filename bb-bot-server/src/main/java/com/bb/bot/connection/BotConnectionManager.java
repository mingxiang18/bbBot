package com.bb.bot.connection;

import com.bb.bot.config.*;
import com.bb.bot.connection.discord.DiscordMessageListener;
import com.bb.bot.connection.bb.BbWebSocketServer;
import com.bb.bot.connection.onebot.OnebotWebSocketClient;
import com.bb.bot.connection.onebot.OnebotWebSocketServer;
import com.bb.bot.connection.qq.QqApiCaller;
import com.bb.bot.connection.qq.QqWebSocketClient;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.commons.lang3.StringUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.server.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 机器人连接
 */
@Slf4j
@Configuration
public class BotConnectionManager {

    @Autowired
    private BotConfig botConfig;

    @Autowired
    private QqApiCaller qqApiCaller;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Getter
    private List<WebSocketClient> webSocketClientList = new ArrayList<>();

    @Getter
    private List<WebSocketServer> webSocketServerList = new ArrayList<>();

    @Getter
    private List<JDA> discordClientList = new ArrayList<>();

    @PostConstruct
    public void initBotConnections() {
        //注册qq机器人连接
        for (Map.Entry<String, QqConfig> qqConfigEntry : botConfig.getQq().entrySet()) {
            initQqBotConnection(qqConfigEntry.getKey(), qqConfigEntry.getValue());
        }
        //注册onebot机器人连接
        for (Map.Entry<String, OnebotConfig> onebotConfigEntry : botConfig.getOnebot().entrySet()) {
            initOnebotConnection(onebotConfigEntry.getKey(), onebotConfigEntry.getValue());
        }
        //注册bb机器人连接
        for (Map.Entry<String, BbConfig> bbConfigEntry : botConfig.getBb().entrySet()) {
            initBbConnection(bbConfigEntry.getKey(), bbConfigEntry.getValue());
        }
        //注册discord机器人连接
        for (Map.Entry<String, DiscordConfig> discordConfigEntry : botConfig.getDiscord().entrySet()) {
            initDiscordConnection(discordConfigEntry.getKey(), discordConfigEntry.getValue());
        }
    }

    /**
     * 初始化bb机器人连接
     */
    private void initBbConnection(String botName, BbConfig bbConfig) {
        if (bbConfig.isEnable()) {
            webSocketServerList.add(new BbWebSocketServer(botName, bbConfig, publisher));
        }
    }

    /**
     * 初始化qq机器人连接
     */
    private void initQqBotConnection(String botName, QqConfig qqConfig) {
        if (qqConfig.isEnable() && "websocket".equals(qqConfig.getType())) {
            // 初始化连接
            webSocketClientList.add(new QqWebSocketClient(botName, qqConfig, publisher, qqApiCaller, URI.create(qqApiCaller.getWebSocketUrl(qqConfig))));
        }
    }

    /**
     * 初始化onebot机器人连接
     */
    private void initOnebotConnection(String botName, OnebotConfig onebotConfig) {
        if (onebotConfig.isEnable()) {
            WebSocketConfig webSocket = onebotConfig.getWebSocket();
            //初始化客户端连接
            if (webSocket.getClient() != null && StringUtils.isNoneBlank(webSocket.getClient().getServerUrl())) {
                webSocketClientList.add(new OnebotWebSocketClient(botName, onebotConfig, publisher, URI.create(webSocket.getClient().getServerUrl())));
            }
            //初始化服务端端口
            if (webSocket.getServer() != null && webSocket.getServer().getPort() > 0) {
                webSocketServerList.add(new OnebotWebSocketServer(botName, onebotConfig, publisher, webSocket.getServer().getPort()));
            }
        }
    }

    /**
     * 初始化discord机器人连接
     */
    private void initDiscordConnection(String botName, DiscordConfig discordConfig) {
        if (!discordConfig.isEnable()) {
            return;
        }
        if (StringUtils.isBlank(discordConfig.getToken())) {
            log.error("discord机器人{}已启用但token为空，跳过初始化", botName);
            return;
        }
        try {
            discordConfig.setBotName(botName);
            JDA jda = JDABuilder.createDefault(discordConfig.getToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordMessageListener(botName, discordConfig, publisher))
                    .build();
            discordConfig.setJda(jda);
            discordClientList.add(jda);
            log.info("discord机器人{}初始化完成", botName);
        } catch (Exception e) {
            log.error("discord机器人{}初始化失败", botName, e);
        }
    }

    @PreDestroy
    public void destroyBotConnections() {
        for (JDA jda : discordClientList) {
            try {
                jda.shutdown();
            } catch (Exception e) {
                log.error("discord机器人连接关闭失败", e);
            }
        }
    }
}
