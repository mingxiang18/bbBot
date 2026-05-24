package com.bb.bot.common.util.nso;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.handler.nso.BbNsoHandler;
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
 * {@link SplatoonTokenManager#getValid} 内部 {@code loadAll} 始终按 USER_INFO、WEB_ACCESS_TOKEN、
 * CORAL_USER_ID、WEB_SERVICE_TOKEN、BULLET_TOKEN 顺序查询。
 */
@ExtendWith(MockitoExtension.class)
class SplatoonTokenManagerTest {

    @Mock
    IUserConfigValueService userConfigValueService;

    @Mock
    Splatoon3ApiCaller splatoon3ApiCaller;

    @Mock
    BbNsoHandler bbNsoHandler;

    @InjectMocks
    SplatoonTokenManager tokenManager;

    @Test
    void getValid_allTokensPresent_pingOk_returnsExisting() {
        UserConfigValue userInfoRow = row("userInfo", "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row("webServiceToken", "ws-cur");
        UserConfigValue bullet = row("bulletToken", "bullet-cur");

        // loadAll 的 5 次顺序：USER_INFO, WEB_ACCESS_TOKEN, CORAL_USER_ID, WEB_SERVICE_TOKEN, BULLET_TOKEN
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userInfoRow)
                .thenReturn(null) // webAccessToken — 不影响存在性检查
                .thenReturn(null) // coralUserId — 同上
                .thenReturn(ws)
                .thenReturn(bullet);

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-cur", token.webServiceToken());
        assertEquals("bullet-cur", token.bulletToken());
        verify(splatoon3ApiCaller).getTest(eq("bullet-cur"), eq("ws-cur"), any(JSONObject.class));
        verify(bbNsoHandler, never()).resetUserToken(anyString());
    }

    @Test
    void getValid_pingThrowsUnauthorized_triggersRefresh() {
        UserConfigValue userInfoRow = row("userInfo", "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row("webServiceToken", "ws-old");
        UserConfigValue bullet = row("bulletToken", "bullet-old");
        UserConfigValue webAccess = row("webAccessToken", "wa");
        UserConfigValue coral = row("coralUserId", "coral");

        // 第一次 loadAll: 5 次（全有）
        // 第二次 loadAll（refresh 内）: 5 次（依然全有）
        // 之后 upsert 各做一次 getOne (web service + bullet) + save/updateById
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                // 1st loadAll
                .thenReturn(userInfoRow).thenReturn(webAccess).thenReturn(coral).thenReturn(ws).thenReturn(bullet)
                // 2nd loadAll inside refresh
                .thenReturn(userInfoRow).thenReturn(webAccess).thenReturn(coral).thenReturn(ws).thenReturn(bullet)
                // upsert WEB_SERVICE: existing
                .thenReturn(ws)
                // upsert BULLET: existing
                .thenReturn(bullet);

        when(splatoon3ApiCaller.getTest(anyString(), anyString(), any(JSONObject.class)))
                .thenThrow(new Splatoon3ApiException(
                        Splatoon3ApiException.ErrorType.UNAUTHORIZED, 401, "expired", null));
        when(splatoon3ApiCaller.getWebServiceToken(any(JSONObject.class), eq("wa"), eq("coral")))
                .thenReturn("ws-new");
        when(splatoon3ApiCaller.getBulletToken(eq("ws-new"), any(JSONObject.class)))
                .thenReturn("bullet-new");

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-new", token.webServiceToken());
        assertEquals("bullet-new", token.bulletToken());
        verify(bbNsoHandler, times(1)).resetUserToken("U1");
    }

    @Test
    void getValid_pingThrowsLegacy401String_triggersRefresh() {
        UserConfigValue userInfoRow = row("userInfo", "{\"id\":\"acc-1\",\"language\":\"zh-CN\"}");
        UserConfigValue ws = row("webServiceToken", "ws-old");
        UserConfigValue bullet = row("bulletToken", "bullet-old");
        UserConfigValue webAccess = row("webAccessToken", "wa");
        UserConfigValue coral = row("coralUserId", "coral");

        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(userInfoRow).thenReturn(webAccess).thenReturn(coral).thenReturn(ws).thenReturn(bullet)
                .thenReturn(userInfoRow).thenReturn(webAccess).thenReturn(coral).thenReturn(ws).thenReturn(bullet)
                .thenReturn(ws)
                .thenReturn(bullet);

        when(splatoon3ApiCaller.getTest(anyString(), anyString(), any(JSONObject.class)))
                .thenThrow(new RuntimeException("HTTP 401 Unauthorized"));
        when(splatoon3ApiCaller.getWebServiceToken(any(JSONObject.class), anyString(), anyString()))
                .thenReturn("ws-new");
        when(splatoon3ApiCaller.getBulletToken(anyString(), any(JSONObject.class)))
                .thenReturn("bullet-new");

        SplatoonTokenManager.SplatoonToken token = tokenManager.getValid("U1");

        assertEquals("ws-new", token.webServiceToken());
        verify(bbNsoHandler).resetUserToken("U1");
    }

    @Test
    void refresh_missingUserInfo_throwsFatal() {
        // loadAll 的 USER_INFO 槽位返回 null，其余无所谓
        when(userConfigValueService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null) // userInfo missing
                .thenReturn(row("webAccessToken", "wa"))
                .thenReturn(row("coralUserId", "coral"))
                .thenReturn(null)
                .thenReturn(null);

        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> tokenManager.refresh("U1"));
        assertSame(Splatoon3ApiException.ErrorType.FATAL, ex.getErrorType());
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
