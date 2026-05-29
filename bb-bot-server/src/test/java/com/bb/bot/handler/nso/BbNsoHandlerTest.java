package com.bb.bot.handler.nso;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.nso.NsoApiCaller;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link BbNsoHandler} 的回复路径已迁移到 {@link BbReplies}，
 * 且各决策分支的回复文本与重构前一致（行为等价）。
 */
class BbNsoHandlerTest {

    private BbReplies bbReplies;
    private NsoApiCaller nsoApiCaller;
    private IUserConfigValueService userConfigValueService;
    private BbNsoHandler handler;

    @BeforeEach
    void setUp() {
        bbReplies = mock(BbReplies.class);
        nsoApiCaller = mock(NsoApiCaller.class);
        userConfigValueService = mock(IUserConfigValueService.class);

        handler = new BbNsoHandler();
        ReflectionTestUtils.setField(handler, "bbReplies", bbReplies);
        ReflectionTestUtils.setField(handler, "nsoApiCaller", nsoApiCaller);
        ReflectionTestUtils.setField(handler, "userConfigValueService", userConfigValueService);
    }

    private BbReceiveMessage receiveMessage(String message) {
        BbReceiveMessage src = new BbReceiveMessage();
        src.setBotType("qq");
        src.setMessageType("group");
        src.setUserId("u-123");
        src.setGroupId("g-456");
        src.setMessageId("m-789");
        src.setMessage(message);
        return src;
    }

    @Test
    void loginNso_should_reply_login_url_via_atText() {
        when(nsoApiCaller.getUserLoginInUrl(any(), any())).thenReturn("https://login.example/x");

        BbReceiveMessage src = receiveMessage("登录nso");
        handler.loginNso(src);

        verify(bbReplies).atText(src, "请点击以下连接进行nso登录：https://login.example/x");
    }

    @Test
    void loginNsoApp_success_should_reply_done_via_atText() {
        when(nsoApiCaller.getSessionToken(any(), any())).thenReturn("session-token-x");
        // resetUserToken 链路 stub
        JSONObject userToken = new JSONObject();
        userToken.put("access_token", "at");
        userToken.put("id_token", "it");
        when(nsoApiCaller.getUserToken("session-token-x")).thenReturn(userToken);
        when(nsoApiCaller.getUserInfo("at")).thenReturn(new JSONObject());

        JSONObject webLoginToken = JSONObject.parseObject(
                "{\"result\":{\"webApiServerCredential\":{\"accessToken\":\"wat\"},\"user\":{\"id\":\"coral-1\"}}}");
        when(nsoApiCaller.getLoginToken(any(), eq("it"))).thenReturn(webLoginToken);

        UserConfigValue sessionTokenConfig = new UserConfigValue();
        sessionTokenConfig.setValueName("session-token-x");
        when(userConfigValueService.getOne(any())).thenReturn(sessionTokenConfig);

        BbReceiveMessage src = receiveMessage("设置nso登录码 abc");
        handler.loginNsoApp(src);

        verify(bbReplies).atText(src, "已完成设置");
    }

    @Test
    void loginNsoApp_failure_should_reply_error_via_atText() {
        when(nsoApiCaller.getSessionToken(any(), any())).thenThrow(new RuntimeException("boom"));

        BbReceiveMessage src = receiveMessage("设置nso登录码 abc");
        handler.loginNsoApp(src);

        verify(bbReplies).atText(src, "设置出现异常，请尝试重新设置");
    }

    @Test
    void getNsSwCode_should_reply_sw_code_via_atText() {
        UserConfigValue webAccessTokenConfig = new UserConfigValue();
        webAccessTokenConfig.setValueName("wat");
        when(userConfigValueService.getOne(any())).thenReturn(webAccessTokenConfig);

        JSONObject accountInfo = JSONObject.parseObject(
                "{\"status\":200,\"result\":{\"links\":{\"friendCode\":{\"id\":\"1234-5678-9012\"}}}}");
        when(nsoApiCaller.getNsAccountInfo("wat")).thenReturn(accountInfo);

        BbReceiveMessage src = receiveMessage("sw码");
        handler.getNsSwCode(src);

        verify(bbReplies).atText(src, "SW-1234-5678-9012");
    }

    @Test
    void getNsFriendList_should_reply_assembled_list_via_atText() {
        UserConfigValue webAccessTokenConfig = new UserConfigValue();
        webAccessTokenConfig.setValueName("wat");
        when(userConfigValueService.getOne(any())).thenReturn(webAccessTokenConfig);

        JSONObject friendList = JSONObject.parseObject(
                "{\"status\":200,\"result\":{\"friends\":["
                        + "{\"name\":\"Alice\",\"presence\":{\"state\":\"ONLINE\",\"game\":{\"name\":\"Splatoon\"}}}"
                        + "]}}");
        when(nsoApiCaller.getNsFriendList("wat")).thenReturn(friendList);

        BbReceiveMessage src = receiveMessage("ns好友");
        handler.getNsFriendList(src);

        String expected = "好友数：1\n页数：1/1\n好友名：【Alice】（在线，游戏中：Splatoon）\n";
        verify(bbReplies).atText(src, expected);
    }
}
