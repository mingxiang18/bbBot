package com.bb.onebot.util.qq;

import com.bb.onebot.entity.qq.SendChannelMessage;
import com.bb.onebot.util.LocalCacheUtils;
import com.bb.onebot.util.RestClient;
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
public class QqUtils {

    @Autowired
    private RestClient restClient;

    @Value("${qq.appId}")
    private String appId;

    @Value("${qq.clientSecret}")
    private String clientSecret;

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
    public String getToken() {
        String qqToken = LocalCacheUtils.getCacheObject("qq.token");
        if (StringUtils.isEmpty(qqToken)) {
            //调用接口获取token
            Map<String, String> request = new HashMap<>();
            request.put("appId", appId);
            request.put("clientSecret", clientSecret);
            Map response = restClient.post(getAppAccessTokenUrl, request, Map.class);
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
    public String getWebSocketUrl() {
        Map response = getForQQ(baseUrl + gatewayUrl, null, Map.class);
        return (String) response.get("url");
    }

    /**
     * 发送频道消息
     */
    public void sendChannelMessage(String channelId, SendChannelMessage channelMessage) {
        postForQQ(baseUrl + sendChannelMessageUrl.replace("{channel_id}", channelId),
                channelMessage, String.class);
    }

    /**
     * qq的api通用请求get方法
     */
    private  <T> T getForQQ(String url, Object request, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", getToken());
        httpHeaders.set("X-Union-Appid", appId);
        return restClient.get(url, httpHeaders, clazz);
    }

    /**
     * qq的api通用请求post方法
     */
    private  <T> T postForQQ(String url, Object request, Class<T> clazz) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", getToken());
        httpHeaders.set("X-Union-Appid", appId);
        httpHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return restClient.post(url, httpHeaders, request, clazz);
    }
}
