package com.bb.bot.connection.qq;

import com.bb.bot.config.QqConfig;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.common.util.LocalCacheUtils;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.entity.qq.GroupMessage;
import com.bb.bot.entity.qq.UploadMediaRequest;
import com.bb.bot.entity.qq.UploadMediaResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    @Value("${qq.sendGroupMessageUrl:/v2/groups/{group_openid}/messages}")
    private String sendGroupMessageUrl;

    @Value("${qq.sendChannelMessageUrl:/channels/{channel_id}/messages}")
    private String sendChannelMessageUrl;

    @Value("${qq.sendC2CMessageUrl:/v2/users/{openid}/messages}")
    private String sendC2CMessageUrl;

    @Value("${qq.sendDirectMessageUrl:/dms/{guild_id}/messages}")
    private String sendDirectMessageUrl;

    @Value("${qq.uploadGroupMediaUrl:/v2/groups/{group_openid}/files}")
    private String uploadGroupMediaUrl;

    @Value("${qq.uploadC2CMediaUrl:/v2/users/{openid}/files}")
    private String uploadC2CMediaUrl;

    /**
     * 缓存 TTL 比官方 expires_in 提前的安全余量（秒）：保证缓存里绝不残留已过期 token。
     */
    private static final long EXPIRE_SAFETY_SEC = 30;

    /**
     * 定时任务「提前刷新」阈值（秒）：剩余有效期不足这么多就换新 token。
     * 官方在过期前 60 秒内重新获取会拿到新 token 且旧 token 仍有效，故 120s 提前换可无缝衔接。
     */
    private static final long REFRESH_AHEAD_SEC = 120;

    /**
     * appId -> token 过期 epoch 秒。仅供定时任务判断「是否该提前刷新」；缓存本身的过期由
     * {@link LocalCacheUtils} 的 TTL 兜底（即便定时任务没跑，过期后也会现拉，不会用到旧 token）。
     */
    private final Map<String, Long> tokenExpireAtSec = new ConcurrentHashMap<>();

    private static String tokenCacheKey(QqConfig qqConfig) {
        return "qq.token." + qqConfig.getAppId();
    }

    /**
     * 获取 token（请求热路径）。命中缓存直接返回，绝不在这里同步现拉——靠
     * {@link QqTokenRefreshSchedule} 提前刷新保持缓存常热；仅缓存为空（冷启动/异常）时才兜底现拉。
     */
    public String getToken(QqConfig qqConfig) {
        String qqToken = LocalCacheUtils.getCacheObject(tokenCacheKey(qqConfig));
        if (StringUtils.isNotEmpty(qqToken)) {
            return qqToken;
        }
        return refreshToken(qqConfig);
    }

    /**
     * 强制拉取新 token、写缓存并记录过期时间，返回新 token。{@code synchronized} 避免并发重复拉取；
     * 进入后二次确认：若已有别的线程刚刷出有效且未临近过期的 token，则直接复用、不再请求。
     */
    public synchronized String refreshToken(QqConfig qqConfig) {
        String existing = LocalCacheUtils.getCacheObject(tokenCacheKey(qqConfig));
        if (StringUtils.isNotEmpty(existing) && !nearExpiry(qqConfig)) {
            return existing;
        }

        Map<String, String> request = new HashMap<>();
        request.put("appId", qqConfig.getAppId());
        request.put("clientSecret", qqConfig.getClientSecret());
        Map response = restUtils.post(getAppAccessTokenUrl, request, Map.class);

        long expiresIn = Long.parseLong((String) response.get("expires_in"));
        String token = "QQBot " + response.get("access_token");
        LocalCacheUtils.setCacheObject(tokenCacheKey(qqConfig), token, expiresIn - EXPIRE_SAFETY_SEC, ChronoUnit.SECONDS);
        tokenExpireAtSec.put(qqConfig.getAppId(), Instant.now().getEpochSecond() + expiresIn);
        log.info("刷新 QQ access_token，appId={}，有效期 {}s", qqConfig.getAppId(), expiresIn);
        return token;
    }

    /**
     * token 是否已临近过期（剩余有效期不足 {@link #REFRESH_AHEAD_SEC}）。
     * 无记录（从未拉过）也视为需要刷新。供 {@link QqTokenRefreshSchedule} 判断。
     */
    public boolean nearExpiry(QqConfig qqConfig) {
        Long expireAt = tokenExpireAtSec.get(qqConfig.getAppId());
        return expireAt == null || Instant.now().getEpochSecond() >= expireAt - REFRESH_AHEAD_SEC;
    }

    /**
     * 获取websocket地址
     */
    public String getWebSocketUrl(QqConfig qqConfig) {
        Map response = getForQQ(qqConfig, baseUrl + gatewayUrl, null, Map.class);
        return (String) response.get("url");
    }

    /**
     * 发送群组消息
     */
    public void sendGroupMessage(QqConfig qqConfig, String groupOpenId, GroupMessage groupMessage) {
        postForQQ(qqConfig, baseUrl + sendGroupMessageUrl.replace("{group_openid}", groupOpenId),
                groupMessage, String.class);
    }

    /**
     * 发送频道消息
     */
    public void sendChannelMessage(QqConfig qqConfig, String channelId, ChannelMessage channelMessage) {
        postForQQ(qqConfig, baseUrl + sendChannelMessageUrl.replace("{channel_id}", channelId),
                channelMessage, String.class);
    }

    /**
     * 发送单聊（C2C 普通私聊）消息
     */
    public void sendC2CMessage(QqConfig qqConfig, String userOpenId, GroupMessage groupMessage) {
        postForQQ(qqConfig, baseUrl + sendC2CMessageUrl.replace("{openid}", userOpenId),
                groupMessage, String.class);
    }

    /**
     * 发送频道私信消息
     */
    public void sendDirectMessage(QqConfig qqConfig, String guildId, ChannelMessage channelMessage) {
        postForQQ(qqConfig, baseUrl + sendDirectMessageUrl.replace("{guild_id}", guildId),
                channelMessage, String.class);
    }

    /**
     * 上传群组富媒体消息
     */
    public UploadMediaResponse uploadGroupMedia(QqConfig qqConfig, String groupOpenId, UploadMediaRequest uploadMediaRequest) {
        return postForQQ(qqConfig, baseUrl + uploadGroupMediaUrl.replace("{group_openid}", groupOpenId),
                uploadMediaRequest, UploadMediaResponse.class);
    }

    /**
     * 上传单聊（C2C）富媒体消息
     */
    public UploadMediaResponse uploadC2CMedia(QqConfig qqConfig, String userOpenId, UploadMediaRequest uploadMediaRequest) {
        return postForQQ(qqConfig, baseUrl + uploadC2CMediaUrl.replace("{openid}", userOpenId),
                uploadMediaRequest, UploadMediaResponse.class);
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
