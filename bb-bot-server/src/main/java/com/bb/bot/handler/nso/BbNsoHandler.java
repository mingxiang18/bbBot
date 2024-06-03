package com.bb.bot.handler.nso;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.NsoApiCaller;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.util.LocalCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任天堂online登录事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB)
public class BbNsoHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    /**
     * 获取登录url
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"登录nso", "/登录nso"}, name = "登录nso")
    public void loginNso(BbReceiveMessage bbReceiveMessage) {
        //编码示例
        //1
        //b'^(w\xb4v\xaf\x1d\xe4w^\x178o\x97\x0f\xbb\x93\x86\x86)P\xb9\xde\x1c:\xba\xa6t\xdf\xe3\xdbI\x88\xdf\xfb\xf2'
        //byte[] randomByte1 = DatatypeConverter.parseHexBinary("5e2877b476af1de4775e17386f970fbb9386862950b9de1c3abaa674dfe3db4988dffbf2");
        //b'Xih3tHavHeR3Xhc4b5cPu5OGhilQud4cOrqmdN_j20mI3_vy'
        //2
        //b"\x8d\xe5\x03\x1b\x02\xd1BR\xed\xf95\x19\xe1E'\x10\x1e\xe18\x117\x02$\xce\xb8\x0b\xa3s\xf5\xf5p\x97"
        //byte[] randomByte2 = DatatypeConverter.parseHexBinary("8de5031b02d14252edf93519e14527101ee13811370224ceb80ba373f5f57097");
        //b'jeUDGwLRQlLt-TUZ4UUnEB7hOBE3AiTOuAujc_X1cJc='
        //3
        //b'mONAyEVT19AIgOi9JikGbFjY46Xvyos8DwgYaPhGDeM='

        //生成随机授权编码
        byte[] randomByte1 = NsoApiCaller.getRandom(36);
        String authState = NsoApiCaller.safeUrlBase64Encode(randomByte1);
        byte[] randomByte2 = NsoApiCaller.getRandom(32);
        String authCodeVerifier = NsoApiCaller.safeUrlBase64Encode(randomByte2);
        String authCodeChallenge = NsoApiCaller.safeUrlBase64Encode(NsoApiCaller.hashEncode(authCodeVerifier));

        //打印相关随机编码
        log.info("auth_state: " + authState);
        log.info("auth_code_verifier: " + authCodeVerifier);
        log.info("auth_code_challenge: " + authCodeChallenge);

        //将随机编码设置到缓存
        LocalCacheUtils.setCacheObject(bbReceiveMessage.getUserId() + "-" + "auth_state", authState, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject(bbReceiveMessage.getUserId() + "-" + "auth_code_verifier", authCodeVerifier, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject(bbReceiveMessage.getUserId() + "-" + "auth_code_challenge", authCodeChallenge, 5, ChronoUnit.MINUTES);

        //获取登录url
        String userLoginInUrl = nsoApiCaller.getUserLoginInUrl(authState, authCodeChallenge);

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent("请点击以下连接进行nso登录：" + userLoginInUrl))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 保存session_token并进行登录获取token
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.FUZZY, keyword = {"设置nso登录码", "/设置nso登录码"}, name = "设置nso登录码")
    public void loginNsoApp(BbReceiveMessage bbReceiveMessage) {
        try {
            //删除qq自带信息后获取登录码内容
            String loginInAnswer = bbReceiveMessage.getMessage().replaceAll("/?设置nso登录码\\s?", "");
            //通过登陆码获取session_token
            String sessionToken = nsoApiCaller.getSessionToken(loginInAnswer,
                    LocalCacheUtils.getCacheObject(bbReceiveMessage.getUserId() + "-" + "auth_code_verifier"));

            //重新设置session_token
            UserConfigValue userConfigValue = new UserConfigValue();
            userConfigValue.setUserId(bbReceiveMessage.getUserId());
            userConfigValue.setType("NSO");
            userConfigValue.setKeyName("session_token");
            userConfigValue.setValueName(sessionToken);
            userConfigValueService.resetUserConfigValue(userConfigValue);

            //重新设置账户token
            resetUserToken(bbReceiveMessage.getUserId());

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("已完成设置"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
        }catch (Exception e) {
            log.error("登录nso失败", e);

            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("设置出现异常，请尝试重新设置"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
        }
    }

    /**
     * 获取自己账户ns的sw码
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"sw码", "/sw码"}, name = "获取自己的sw码")
    public void getNsSwCode(BbReceiveMessage bbReceiveMessage) {
        //获取用户的accessToken
        UserConfigValue webAccessTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "webAccessToken"));
        if(webAccessTokenConfig == null) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("当前用户未设置nso登录码"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
        }

        JSONObject accountInfo = nsoApiCaller.getNsAccountInfo(webAccessTokenConfig.getValueName());

        //如果token过期，重新设置token并获取用户信息
        if (9404 == accountInfo.getInteger("status")) {
            resetUserToken(bbReceiveMessage.getUserId());
            //获取token
            webAccessTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "webAccessToken"));
            accountInfo = nsoApiCaller.getNsAccountInfo(webAccessTokenConfig.getValueName());
        }

        String swCode = "SW-" + accountInfo.getJSONObject("result").getJSONObject("links").getJSONObject("friendCode").getString("id");

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent(swCode))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 获取ns好友列表
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^ns好友", "^/ns好友"}, name = "获取好友列表")
    public void getNsFriendList(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("ns好友(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        Integer pageNum = 1;
        Integer pageSize = 10;
        Integer pageStart = 0;
        // 如果找到匹配项
        if (matcher.find()) {
            pageNum = Integer.valueOf(matcher.group(1));
        }
        pageStart = (pageNum-1) * pageSize;

        //获取用户的accessToken
        UserConfigValue webAccessTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "webAccessToken"));
        //如果为空，告诉用户未设置nso登录码
        if(webAccessTokenConfig == null) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("当前用户未设置nso登录码"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
        }

        StringBuilder returnMessage = new StringBuilder();
        JSONObject nsFriendList = nsoApiCaller.getNsFriendList(webAccessTokenConfig.getValueName());

        //如果token过期，重新设置token并获取好友列表
        if (9404 == nsFriendList.getInteger("status")) {
            resetUserToken(bbReceiveMessage.getUserId());
            //获取token
            webAccessTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "webAccessToken"));
            nsFriendList = nsoApiCaller.getNsFriendList(webAccessTokenConfig.getValueName());
        }

        //拼装好友登录状态
        JSONArray friendMessageList = nsFriendList.getJSONObject("result").getJSONArray("friends");
        returnMessage.append("好友数：" + friendMessageList.size() + "\n");
        returnMessage.append("页数：" + pageNum + "/" + (friendMessageList.size() % pageSize == 0 ? (friendMessageList.size() / pageSize) : (friendMessageList.size() / pageSize + 1)) + "\n");

        for (int i = pageStart; i < friendMessageList.size() && i < pageStart + pageSize; i++) {
            JSONObject friendMessage = friendMessageList.getJSONObject(i);
            String state = friendMessage.getJSONObject("presence").getString("state");
            if ("ONLINE".equals(state)) {
                JSONObject gameInfo = friendMessage.getJSONObject("presence").getJSONObject("game");
                state = "在线" + (gameInfo == null ? "" : "，游戏中：" + gameInfo.getString("name"));
            }else if ("OFFLINE".equals(state)) {
                state = "离线，上次在线：" + LocalDateTime.ofEpochSecond(friendMessage.getJSONObject("presence").getLong("updatedAt"), 0, ZoneOffset.ofHours(8)).format(DateUtils.normalTimePattern);
            }

            returnMessage.append("好友名：【" + friendMessage.getString("name") + "】" +
                    "（" + state + "）\n");
        }

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent(returnMessage.toString()))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 刷新用户token
     */
    public void resetUserToken(String userId) {
        //如果accessToken不存在，获取用户的sessionToken
        UserConfigValue sessionTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "session_token"));
        if (sessionTokenConfig == null) {
            throw new RuntimeException("未设置nso登录码，无法获取信息");
        }
        String sessionToken = sessionTokenConfig.getValueName();

        //获取用户token
        JSONObject userToken = nsoApiCaller.getUserToken(sessionToken);
        String accessToken = userToken.getString("access_token");
        String idToken = userToken.getString("id_token");

        //获取用户账号信息
        JSONObject userInfo = nsoApiCaller.getUserInfo(accessToken);

        //重新设置用户账号信息
        UserConfigValue userInfoConfig = new UserConfigValue();
        userInfoConfig.setUserId(userId);
        userInfoConfig.setType("NSO");
        userInfoConfig.setKeyName("userInfo");
        userInfoConfig.setValueName(userInfo.toJSONString());
        userConfigValueService.resetUserConfigValue(userInfoConfig);

        //获取登录token
        JSONObject webLoginToken = nsoApiCaller.getLoginToken(userInfo, idToken);
        String webAccessToken = webLoginToken.getJSONObject("result").getJSONObject("webApiServerCredential").getString("accessToken");
        String coralUserId  = webLoginToken.getJSONObject("result").getJSONObject("user").getString("id");

        //重新设置token信息
        UserConfigValue accessTokenConfig = new UserConfigValue();
        accessTokenConfig.setUserId(userId);
        accessTokenConfig.setType("NSO");
        accessTokenConfig.setKeyName("webAccessToken");
        accessTokenConfig.setValueName(webAccessToken);
        userConfigValueService.resetUserConfigValue(accessTokenConfig);
        //重新设置coralUserId信息
        UserConfigValue coralUserIdConfig = new UserConfigValue();
        coralUserIdConfig.setUserId(userId);
        coralUserIdConfig.setType("NSO");
        coralUserIdConfig.setKeyName("coralUserId");
        coralUserIdConfig.setValueName(coralUserId);
        userConfigValueService.resetUserConfigValue(coralUserIdConfig);
    }
}
