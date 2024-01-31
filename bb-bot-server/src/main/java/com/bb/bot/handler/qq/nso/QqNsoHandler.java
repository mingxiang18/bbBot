package com.bb.bot.handler.qq.nso;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.NsoApiCaller;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.dispatcher.qq.QqEventDispatcher;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.util.LocalCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.temporal.ChronoUnit;

/**
 * 任天堂online登录事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.QQ)
public class QqNsoHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    /**
     * 获取登录url
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"登录nso", "/登录nso"}, name = "登录nso")
    public void loginNso(QqMessage event) {
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
        LocalCacheUtils.setCacheObject(event.getAuthor().getId() + "-" + "auth_state", authState, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject(event.getAuthor().getId() + "-" + "auth_code_verifier", authCodeVerifier, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject(event.getAuthor().getId() + "-" + "auth_code_challenge", authCodeChallenge, 5, ChronoUnit.MINUTES);

        //获取登录url
        String userLoginInUrl = nsoApiCaller.getUserLoginInUrl(authState, authCodeChallenge);

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent("请点击以下连接进行nso登录：" + userLoginInUrl);
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    /**
     * 保存session_token并进行登录获取token
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.FUZZY, keyword = {"设置nso登录码", "/设置nso登录码"}, name = "设置nso登录码")
    public void loginNsoApp(QqMessage event) {
        try {
            //删除qq自带信息后获取登录码内容
            String loginInAnswer = event.getContent().replaceAll(QqEventDispatcher.atCompileReg, "").replaceAll("/?设置nso登录码\\s?", "");
            //通过登陆码获取session_token
            String sessionToken = nsoApiCaller.getSessionToken(loginInAnswer,
                    LocalCacheUtils.getCacheObject(event.getAuthor().getId() + "-" + "auth_code_verifier"));

            //先删除原来的设置记录
            userConfigValueService.remove(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, event.getAuthor().getId())
                    .eq(UserConfigValue::getType, "NSO")
                    .eq(UserConfigValue::getKeyName, "session_token"));

            //重新设置
            UserConfigValue userConfigValue = new UserConfigValue();
            userConfigValue.setUserId(event.getAuthor().getId());
            userConfigValue.setType("NSO");
            userConfigValue.setKeyName("session_token");
            userConfigValue.setValueName(sessionToken);
            userConfigValueService.save(userConfigValue);

            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("已完成设置");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }catch (Exception e) {
            log.error("登录nso失败", e);
            ChannelMessage channelMessage = new ChannelMessage();
            channelMessage.setContent("设置出现异常，请尝试重新设置");
            channelMessage.setMsgId(event.getId());
            qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
        }
    }
}
