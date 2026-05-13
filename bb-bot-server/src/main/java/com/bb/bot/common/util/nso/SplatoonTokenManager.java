package com.bb.bot.common.util.nso;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.handler.nso.BbNsoHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 集中管理 Splatoon3 登录所需的四把 token（userInfo / webAccessToken / coralUserId /
 * webServiceToken / bulletToken），把原本散布在 {@code BbSplatoonUserHandler} 里
 * 1063-1143 行的 4 + 4 次 DB 查询 + 字符串匹配 401 的逻辑收敛到一个地方。
 *
 * <p>典型用法：
 * <pre>{@code
 * SplatoonToken token = tokenManager.getValid(userId);
 * try {
 *     return apiCaller.getX(token.bullet(), token.webService(), token.userInfo());
 * } catch (Splatoon3ApiException e) {
 *     if (e.getErrorType() == ErrorType.UNAUTHORIZED) {
 *         token = tokenManager.refresh(userId);
 *         return apiCaller.getX(...);
 *     }
 *     throw e;
 * }
 * }</pre>
 *
 * @author ren
 */
@Slf4j
@Component
public class SplatoonTokenManager {

    public static final String TYPE_NSO = "NSO";
    public static final String KEY_USER_INFO = "userInfo";
    public static final String KEY_WEB_ACCESS_TOKEN = "webAccessToken";
    public static final String KEY_CORAL_USER_ID = "coralUserId";
    public static final String KEY_WEB_SERVICE_TOKEN = "webServiceToken";
    public static final String KEY_BULLET_TOKEN = "bulletToken";

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private BbNsoHandler bbNsoHandler;

    /**
     * 拿到一对可用 token。如果 DB 里缺 webService/bulletToken 或调用 ping 接口返回 401，
     * 则触发 {@link #refresh(String)}。
     */
    public SplatoonToken getValid(String userId) {
        Map<String, UserConfigValue> rows = loadAll(userId);

        UserConfigValue userInfo = rows.get(KEY_USER_INFO);
        UserConfigValue webServiceToken = rows.get(KEY_WEB_SERVICE_TOKEN);
        UserConfigValue bulletToken = rows.get(KEY_BULLET_TOKEN);

        if (userInfo == null || webServiceToken == null || bulletToken == null) {
            return refresh(userId);
        }

        try {
            splatoon3ApiCaller.getTest(bulletToken.getValueName(),
                    webServiceToken.getValueName(),
                    JSONObject.parseObject(userInfo.getValueName()));
        } catch (Splatoon3ApiException e) {
            if (e.getErrorType() == Splatoon3ApiException.ErrorType.UNAUTHORIZED) {
                log.info("Splatoon token unauthorized for user {}, refreshing", userId);
                return refresh(userId);
            }
            throw e;
        } catch (RuntimeException e) {
            // 旧路径：未走 NsoRetryExecutor 的调用兜底
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("Splatoon token unauthorized (legacy match) for user {}, refreshing", userId);
                return refresh(userId);
            }
            log.warn("Splatoon ping failed; reusing existing token", e);
        }

        return new SplatoonToken(
                webServiceToken.getValueName(),
                bulletToken.getValueName(),
                JSONObject.parseObject(userInfo.getValueName()));
    }

    /** 强制刷新一遍。失败抛 {@link Splatoon3ApiException}（FATAL）。 */
    public SplatoonToken refresh(String userId) {
        bbNsoHandler.resetUserToken(userId);

        Map<String, UserConfigValue> rows = loadAll(userId);
        UserConfigValue userInfoRow = required(rows, KEY_USER_INFO, userId);
        UserConfigValue webAccess = required(rows, KEY_WEB_ACCESS_TOKEN, userId);
        UserConfigValue coral = required(rows, KEY_CORAL_USER_ID, userId);

        JSONObject userInfo = JSONObject.parseObject(userInfoRow.getValueName());

        String webService = splatoon3ApiCaller.getWebServiceToken(
                userInfo, webAccess.getValueName(), coral.getValueName());
        upsert(userId, KEY_WEB_SERVICE_TOKEN, webService);

        String bullet = splatoon3ApiCaller.getBulletToken(webService, userInfo);
        upsert(userId, KEY_BULLET_TOKEN, bullet);

        return new SplatoonToken(webService, bullet, userInfo);
    }

    private Map<String, UserConfigValue> loadAll(String userId) {
        Map<String, UserConfigValue> map = new HashMap<>();
        for (String key : new String[]{KEY_USER_INFO, KEY_WEB_ACCESS_TOKEN, KEY_CORAL_USER_ID,
                KEY_WEB_SERVICE_TOKEN, KEY_BULLET_TOKEN}) {
            UserConfigValue row = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                    .eq(UserConfigValue::getUserId, userId)
                    .eq(UserConfigValue::getType, TYPE_NSO)
                    .eq(UserConfigValue::getKeyName, key)
                    .last("limit 1"));
            if (row != null) {
                map.put(key, row);
            }
        }
        return map;
    }

    private UserConfigValue required(Map<String, UserConfigValue> rows, String key, String userId) {
        UserConfigValue row = rows.get(key);
        if (row == null) {
            throw new Splatoon3ApiException(Splatoon3ApiException.ErrorType.FATAL,
                    -1, "Missing NSO config [" + key + "] for user " + userId, null);
        }
        return row;
    }

    private void upsert(String userId, String key, String value) {
        Optional<UserConfigValue> existing = Optional.ofNullable(
                userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                        .eq(UserConfigValue::getUserId, userId)
                        .eq(UserConfigValue::getType, TYPE_NSO)
                        .eq(UserConfigValue::getKeyName, key)
                        .last("limit 1")));
        UserConfigValue row = existing.orElseGet(UserConfigValue::new);
        row.setUserId(userId);
        row.setType(TYPE_NSO);
        row.setKeyName(key);
        row.setValueName(value);
        if (row.getId() == null) {
            userConfigValueService.save(row);
        } else {
            userConfigValueService.updateById(row);
        }
    }

    /** Token 三元组。匿名 record，方便序列化 / 测试。 */
    public record SplatoonToken(String webServiceToken, String bulletToken, JSONObject userInfo) {}
}
