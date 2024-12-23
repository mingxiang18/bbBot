package com.bb.bot.connection.qq;

import com.bb.bot.config.QqConfig;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.common.util.LocalCacheUtils;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * qq官方请求调用工具
 * @author ren
 */
@Slf4j
@Component
public class QqApiCaller {

    @Autowired
    private RestUtils restUtils;

    @Value("${qq.baseUrl:https://api.sgroup.qq.com}")
    private String baseUrl;

    @Value("${qq.getAppAccessTokenUrl:https://bots.qq.com/app/getAppAccessToken}")
    private String getAppAccessTokenUrl;

    @Value("${qq.gatewayUrl:/gateway}")
    private String gatewayUrl;

    @Value("${qq.sendChannelMessageUrl:/channels/{channel_id}/messages}")
    private String sendChannelMessageUrl;

    /**
     * 获取token
     */
    public String getToken(QqConfig qqConfig) {
        String qqToken = LocalCacheUtils.getCacheObject("qq.token");
        if (StringUtils.isEmpty(qqToken)) {
            //调用接口获取token
            Map<String, String> request = new HashMap<>();
            request.put("appId", qqConfig.getAppId());
            request.put("clientSecret", qqConfig.getClientSecret());
            Map response = restUtils.post(getAppAccessTokenUrl, request, Map.class);
            String token = "QQBot " + (String) response.get("access_token");
            //缓存设置token
            LocalCacheUtils.setCacheObject("qq.token",
                    token,
                    Long.valueOf((String) response.get("expires_in")) - 30,
                    ChronoUnit.SECONDS);
            return token;
        }else {
            return qqToken;
        }
    }

    /**
     * 获取websocket地址
     */
    public String getWebSocketUrl(QqConfig qqConfig) {
        Map response = getForQQ(qqConfig, baseUrl + gatewayUrl, null, Map.class);
        return (String) response.get("url");
    }

    /**
     * 发送频道消息
     */
    public void sendChannelMessage(QqConfig qqConfig, String channelId, ChannelMessage channelMessage) {
        postForQQ(qqConfig, baseUrl + sendChannelMessageUrl.replace("{channel_id}", channelId),
                channelMessage, String.class);
    }

    /**
     * qq的api通用请求get方法
     */
    private  <T> T getForQQ(QqConfig qqConfig, String url, Object request, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", getToken(qqConfig));
        httpHeaders.set("X-Union-Appid", qqConfig.getAppId());
        return restUtils.get(url, httpHeaders, clazz);
    }

    /**
     * qq的api通用请求post方法
     */
    private  <T> T postForQQ(QqConfig qqConfig, String url, Object request, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", getToken(qqConfig));
        httpHeaders.set("X-Union-Appid", qqConfig.getAppId());
        httpHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return restUtils.post(url, httpHeaders, request, clazz);
    }

    /**
     * qq的api通用请求post方法
     */
    private  <T> T postFormForQQ(QqConfig qqConfig, String url, Object request, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", getToken(qqConfig));
        httpHeaders.set("X-Union-Appid", qqConfig.getAppId());
        return restUtils.postForForm(url, httpHeaders, request, clazz);
    }
}
