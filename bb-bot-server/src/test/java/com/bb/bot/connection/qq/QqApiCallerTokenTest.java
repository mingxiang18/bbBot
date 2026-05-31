package com.bb.bot.connection.qq;

import com.bb.bot.common.util.LocalCacheUtils;
import com.bb.bot.common.util.RestUtils;
import com.bb.bot.config.QqConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QqApiCaller} 的 access_token 缓存 / 提前刷新逻辑单测（mock 网络）。
 *
 * <p>核心目标：验证「请求热路径不再同步现拉 token」——这是修复 QQ 重复发图诱因（回复被
 * token 现拉拖慢、挤出 5s 窗口被重推）的关键。</p>
 */
class QqApiCallerTokenTest {

    private QqApiCaller newCaller(RestUtils restUtils) {
        QqApiCaller caller = new QqApiCaller();
        ReflectionTestUtils.setField(caller, "restUtils", restUtils);
        ReflectionTestUtils.setField(caller, "getAppAccessTokenUrl", "https://bots.qq.com/app/getAppAccessToken");
        return caller;
    }

    private QqConfig config(String appId) {
        // 每个用例用独立 appId，避免共享的 LocalCacheUtils 静态缓存跨用例串味
        LocalCacheUtils.removeCacheObject("qq.token." + appId);
        QqConfig c = new QqConfig();
        c.setAppId(appId);
        c.setClientSecret("secret-" + appId);
        return c;
    }

    private Map<String, String> tokenResponse(String token, String expiresInSec) {
        Map<String, String> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("expires_in", expiresInSec);
        return resp;
    }

    @Test
    void getToken_emptyCache_fetchesOnceThenServesWarmWithoutNetwork() {
        RestUtils restUtils = mock(RestUtils.class);
        when(restUtils.post(any(), any(), eq(Map.class))).thenReturn(tokenResponse("AT-1", "7200"));
        QqApiCaller caller = newCaller(restUtils);
        QqConfig cfg = config("app-warm");

        String first = caller.getToken(cfg);
        String second = caller.getToken(cfg);
        String third = caller.getToken(cfg);

        assertEquals("QQBot AT-1", first, "token 应带 QQBot 前缀");
        assertEquals("QQBot AT-1", second);
        assertEquals("QQBot AT-1", third);
        // 关键：只在首次冷拉一次，后续走缓存，不再打网络（热路径不阻塞）
        verify(restUtils, times(1)).post(any(), any(), eq(Map.class));
    }

    @Test
    void nearExpiry_freshLongLivedToken_isFalse_shortLived_isTrue() {
        RestUtils restUtils = mock(RestUtils.class);
        QqApiCaller caller = newCaller(restUtils);

        // 长有效期：刷新后远未临近过期
        when(restUtils.post(any(), any(), eq(Map.class))).thenReturn(tokenResponse("AT-long", "7200"));
        QqConfig longCfg = config("app-long");
        caller.refreshToken(longCfg);
        assertFalse(caller.nearExpiry(longCfg), "7200s 的新 token 不应临近过期");

        // 短有效期（< 提前刷新阈值 120s）：刷新后立即就算临近过期，下次巡检会续
        when(restUtils.post(any(), any(), eq(Map.class))).thenReturn(tokenResponse("AT-short", "60"));
        QqConfig shortCfg = config("app-short");
        caller.refreshToken(shortCfg);
        assertTrue(caller.nearExpiry(shortCfg), "60s 的 token 已在 120s 提前刷新阈值内");
    }

    @Test
    void nearExpiry_neverFetched_isTrue() {
        QqApiCaller caller = newCaller(mock(RestUtils.class));
        assertTrue(caller.nearExpiry(config("app-never")), "从未拉过 token 应视为需要刷新");
    }

    @Test
    void refreshToken_notNearExpiry_secondCallReusesWithoutNetwork() {
        RestUtils restUtils = mock(RestUtils.class);
        when(restUtils.post(any(), any(), eq(Map.class))).thenReturn(tokenResponse("AT-x", "7200"));
        QqApiCaller caller = newCaller(restUtils);
        QqConfig cfg = config("app-reuse");

        caller.refreshToken(cfg);
        caller.refreshToken(cfg); // 仍有效且未临近过期 → 二次确认应复用，不再打网络

        verify(restUtils, times(1)).post(any(), any(), eq(Map.class));
    }
}
