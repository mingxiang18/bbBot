package com.bb.bot.handler.qq.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.ImageUploadClient;
import com.bb.bot.common.util.NsoApiCaller;
import com.bb.bot.common.util.Splatoon3ApiCaller;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.handler.qq.nso.QqNsoHandler;
import com.bb.bot.util.RestClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 喷喷获取已登录的用户事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.QQ)
public class QqSplatoonUserHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private RestClient restClient;

    @Autowired
    private ImageUploadClient imageUploadClient;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    @Autowired
    private QqNsoHandler qqNsoHandler;

    /**
     * 获取喷喷好友列表
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.FUZZY, keyword = {"喷喷好友", "/喷喷好友"}, name = "获取喷喷好友列表")
    public void getSplatoon3FriendList(QqMessage event) {
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(event.getAuthor().getId());
        //调用接口获取数据
        JSONObject friends = splatoon3ApiCaller.getFriends(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        StringBuilder returnMessage = new StringBuilder();
        //拼装好友登录状态
        JSONArray friendMessageList = friends.getJSONObject("data").getJSONObject("friends").getJSONArray("nodes");
        for (Object friendMessage : friendMessageList) {
            returnMessage.append("好友名：【" + ((JSONObject) friendMessage).getString("nickname") + "】" +
                    ",  在线状态：" + ((JSONObject) friendMessage).getString("onlineState") + "\n");
        }

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(returnMessage.toString());
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    /**
     * 喷喷用户token实体类
     */
    @Data
    public static class TokenInfo {
        private String webServiceToken;
        private String bulletToken;
        private JSONObject userInfo;

        public TokenInfo(String webServiceToken, String bulletToken, JSONObject userInfo) {
            this.webServiceToken = webServiceToken;
            this.bulletToken = bulletToken;
            this.userInfo = userInfo;
        }
    }

    /**
     * 检查喷喷用户token是否过期并获取token
     */
    private TokenInfo checkAndGetSplatoon3UserToken(String userId) {
        //获取用户的userInfo和webAccessToken
        UserConfigValue userInfoConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "userInfo"));
        UserConfigValue webServiceTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "webServiceToken"));
        UserConfigValue bulletTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "bulletToken"));

        //如果其中一个token为空，重新设置token
        if (userInfoConfig == null || webServiceTokenConfig == null || bulletTokenConfig == null) {
            resetSplatoon3UserToken(userId);
            userInfoConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, userId)
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "userInfo"));
            webServiceTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, userId)
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "webServiceToken"));
            bulletTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, userId)
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "bulletToken"));
        }

        try {
            splatoon3ApiCaller.getTest(bulletTokenConfig.getValueName(), webServiceTokenConfig.getValueName(), JSONObject.parseObject(userInfoConfig.getValueName()));
        }catch (Exception e) {
            //如果token过期，刷新token并获取结果
            if (e.getMessage().contains("401")) {
                resetSplatoon3UserToken(userId);
                userInfoConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "userInfo"));
                webServiceTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "webServiceToken"));
                bulletTokenConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "bulletToken"));
            }else {
                log.error("调用斯普拉遁3接口出错", e);
            }

        }

        return new TokenInfo(webServiceTokenConfig.getValueName(), bulletTokenConfig.getValueName(), JSONObject.parseObject(userInfoConfig.getValueName()));
    }

    /**
     * 刷新喷喷用户token
     */
    private void resetSplatoon3UserToken(String userId) {
        //重新设置nso用户token
        qqNsoHandler.resetUserToken(userId);

        //获取用户的userInfo和webAccessToken
        JSONObject userInfo = JSONObject.parseObject(userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "userInfo"))
                .getValueName());
        String webAccessToken = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "webAccessToken"))
                .getValueName();
        String coralUserId = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, "NSO")
                        .eq(UserConfigValue::getKeyName, "coralUserId"))
                .getValueName();

        //获取斯普拉顿3 web服务token
        String webServiceToken = splatoon3ApiCaller.getWebServiceToken(userInfo, webAccessToken, coralUserId);

        //重新设置web服务token
        UserConfigValue config = new UserConfigValue();
        config.setUserId(userId);
        config.setType("NSO");
        config.setKeyName("webServiceToken");
        config.setValueName(webServiceToken);
        userConfigValueService.resetUserConfigValue(config);

        //获取bulletToken
        String bulletToken = splatoon3ApiCaller.getBulletToken(webServiceToken, userInfo);

        //重新设置bulletToken
        config = new UserConfigValue();
        config.setUserId(userId);
        config.setType("NSO");
        config.setKeyName("bulletToken");
        config.setValueName(bulletToken);
        userConfigValueService.resetUserConfigValue(config);
    }

}
