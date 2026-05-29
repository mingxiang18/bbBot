package com.bb.bot.common.util.nso;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SplatoonTokenManager} 的关键决策路径。
 *
 * <p>注意：MyBatis-Plus 的 {@link LambdaQueryWrapper} 在没有 Spring 容器初始化 lambda cache 时，
 * 无法在 mock 中直接 introspect 它的 SQL 段。本测试用"按调用顺序返回"的策略避开这一限制——
 * {@link SplatoonTokenManager#getValid} 内部 {@code loadAll} 始终按 USER_INFO、
 * WEB_SERVICE_TOKEN、BULLET_TOKEN 顺序查询；refresh 阶段再依次查 dataUser 与三次 upsert。
 *
 * <p>cookie 方案下 token 不再来自 {@code BbNsoHandler}，而是 worker 上的
 * {@link NsoTokenProvider}（按 Android dataUser 取 gtoken + bulletToken）。
 */
@ExtendWith(MockitoExtension.class)
class SplatoonTokenManagerTest {

    @Mock
    IUserConfigValueService userConfigValueService;

    @Mock
    Splatoon3ApiCaller splatoon3ApiCaller;

    @Mock
    NsoTokenProvider nsoTokenProvider;

    @InjectMocks
    SplatoonTokenManager tokenManager;

    @Test
    void getValid_allTokensPresent_pingOk_returnsExisting() {
        UserConfigValue userInfoRow = row(SplatoonTokenManager.KEY_USER_INFO, "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row(SplatoonTokenManager.KEY_WEB_SERVICE_TOKEN, "ws-cur");
        UserConfigValue bullet = row(SplatoonTokenManager.KEY_BULLET_TOKEN, "bullet-cur");

        // loadAll 的 3 次顺序：USER_INFO, WEB_SERVICE_TOKEN, BULLET_TOKEN（全部存在 → 直接 ping）
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userInfoRow)
                .thenReturn(ws)
                .thenReturn(bullet);

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-cur", token.webServiceToken());
        assertEquals("bullet-cur", token.bulletToken());
        verify(splatoon3ApiCaller).getTest(eq("bullet-cur"), eq("ws-cur"), any(JSONObject.class));
        // ping OK 不应触发刷新 → 不调 token-provider
        verify(nsoTokenProvider, never()).fetchToken(anyString());
    }

    @Test
    void getValid_pingThrowsUnauthorized_triggersRefresh() {
        UserConfigValue userInfoRow = row(SplatoonTokenManager.KEY_USER_INFO, "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row(SplatoonTokenManager.KEY_WEB_SERVICE_TOKEN, "ws-old");
        UserConfigValue bullet = row(SplatoonTokenManager.KEY_BULLET_TOKEN, "bullet-old");
        UserConfigValue dataUserRow = row(SplatoonTokenManager.KEY_DATA_USER, "0");

        // 1st loadAll（3 次，全有）→ ping 抛 401 → refresh：
        //   getDataUsers 查 dataUser（1 次）
        //   三次 upsert 各先 getOne（userInfo / webService / bullet）
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                // 1st loadAll
                .thenReturn(userInfoRow).thenReturn(ws).thenReturn(bullet)
                // getDataUsers
                .thenReturn(dataUserRow)
                // upsert userInfo / webService / bullet（existing 各一次）
                .thenReturn(userInfoRow).thenReturn(ws).thenReturn(bullet);

        when(splatoon3ApiCaller.getTest(anyString(), anyString(), any(JSONObject.class)))
                .thenThrow(new Splatoon3ApiException(
                        Splatoon3ApiException.ErrorType.UNAUTHORIZED, 401, "expired", null));

        JSONObject providerResp = new JSONObject();
        providerResp.put("gtoken", "ws-new");
        providerResp.put("bulletToken", "bullet-new");
        when(nsoTokenProvider.fetchToken("0")).thenReturn(providerResp);

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-new", token.webServiceToken());
        assertEquals("bullet-new", token.bulletToken());
        verify(nsoTokenProvider, times(1)).fetchToken("0");
    }

    @Test
    void getValid_pingThrowsLegacy401String_triggersRefresh() {
        UserConfigValue userInfoRow = row(SplatoonTokenManager.KEY_USER_INFO, "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row(SplatoonTokenManager.KEY_WEB_SERVICE_TOKEN, "ws-old");
        UserConfigValue bullet = row(SplatoonTokenManager.KEY_BULLET_TOKEN, "bullet-old");
        UserConfigValue dataUserRow = row(SplatoonTokenManager.KEY_DATA_USER, "0");

        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userInfoRow).thenReturn(ws).thenReturn(bullet)
                .thenReturn(dataUserRow)
                .thenReturn(userInfoRow).thenReturn(ws).thenReturn(bullet);

        // 旧路径：未走 Splatoon3ApiException 而是裸 RuntimeException，靠 message 含 "401" 匹配
        when(splatoon3ApiCaller.getTest(anyString(), anyString(), any(JSONObject.class)))
                .thenThrow(new RuntimeException("HTTP 401 Unauthorized"));

        JSONObject providerResp = new JSONObject();
        providerResp.put("gtoken", "ws-new");
        providerResp.put("bulletToken", "bullet-new");
        when(nsoTokenProvider.fetchToken("0")).thenReturn(providerResp);

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-new", token.webServiceToken());
        verify(nsoTokenProvider).fetchToken("0");
    }

    @Test
    void refresh_tokenProviderInvalidResponse_throwsFatal() {
        UserConfigValue dataUserRow = row(SplatoonTokenManager.KEY_DATA_USER, "0");

        // refresh：getDataUsers 查 dataUser
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(dataUserRow);

        // token-provider 返回缺字段 → NsoTokenProvider.fetchToken 抛错
        JSONObject bad = new JSONObject();
        bad.put("gtoken", "only-gtoken");
        when(nsoTokenProvider.fetchToken("0"))
                .thenThrow(new RuntimeException("NSO token provider 返回无效: " + bad));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> tokenManager.refresh("U1"));
        assertSame(RuntimeException.class, ex.getClass());
    }

    private static UserConfigValue row(String key, String value) {
        UserConfigValue v = new UserConfigValue();
        v.setId(1L);
        v.setUserId("U1");
        v.setType("NSO");
        v.setKeyName(key);
        v.setValueName(value);
        return v;
    }
}
