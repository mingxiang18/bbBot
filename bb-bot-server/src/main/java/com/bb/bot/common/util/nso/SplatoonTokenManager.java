package com.bb.bot.common.util.nso;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 集中管理 Splatoon3 查询所需的 token（userInfo / webServiceToken / bulletToken）。
 * cookie 方案下 token 来自 worker 的 token-provider（读真机 NSO 的 WebView cookie），
 * 按 Android dataUser 区分账号；一个 bbBot 用户可绑定多个账号，查战绩时逐个聚合。
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
    public static final String KEY_WEB_SERVICE_TOKEN = "webServiceToken";
    public static final String KEY_BULLET_TOKEN = "bulletToken";
    /** 该 bbBot 用户绑定的 Android NSO 实例(dataUser):0=主账号, 999=应用双开...,由 owner 绑定。 */
    public static final String KEY_DATA_USER = "dataUser";
    /** SplatNet 返回文本语言:zh-CN 简中(经实测可用,武器/场地名等返回中文)。 */
    public static final String LANGUAGE = "zh-CN";
    /** na_country:中文玩家走 JP 区。只影响请求头 na_country/X-NACOUNTRY。 */
    public static final String COUNTRY = "JP";

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private NsoTokenProvider nsoTokenProvider;

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

        // 强制按当前语言/国家覆盖,避免 DB 里历史存的 en-US 影响返回文本语言
        JSONObject userInfoJson = JSONObject.parseObject(userInfo.getValueName());
        userInfoJson.put("language", LANGUAGE);
        userInfoJson.put("country", COUNTRY);

        try {
            splatoon3ApiCaller.getTest(bulletToken.getValueName(),
                    webServiceToken.getValueName(),
                    userInfoJson);
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
                userInfoJson);
    }

    /**
     * 强制刷新一遍 token。
     *
     * <p>cookie 方案:从 worker 上的 token-provider 拿 gtoken + bulletToken,替代 2024 年中
     * 失效的 imink f-API 链(Pairip 封了模拟器/注入)。token-provider 读真机 NSO 的 WebView
     * cookie,按 dataUser 区分账号。详见 k8s-nso/NSO-COOKIE-METHOD.md。
     */
    public SplatoonToken refresh(String userId) {
        // 单账号路径:取该用户绑定的第一个账号(多账号查询走 getTokenByDataUser 逐个取)
        String dataUser = getDataUsers(userId).get(0);

        SplatoonToken token = getTokenByDataUser(dataUser);

        upsert(userId, KEY_USER_INFO, token.userInfo().toJSONString());
        upsert(userId, KEY_WEB_SERVICE_TOKEN, token.webServiceToken());
        upsert(userId, KEY_BULLET_TOKEN, token.bulletToken());

        return token;
    }

    /**
     * 该 bbBot 用户绑定的全部 Android NSO 账号(dataUser,逗号分隔),未绑定默认 ["0"]。
     * owner 可把一个 userId 绑到多个账号,查战绩时逐个聚合。
     */
    /** 该 bbBot 用户是否已被 owner 绑定喷喷账号(存在 dataUser 配置)。未绑定者禁止使用喷喷战绩功能。 */
    public boolean isBound(String userId) {
        if (userId == null) {
            return false;
        }
        return userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, TYPE_NSO)
                .eq(UserConfigValue::getKeyName, KEY_DATA_USER)
                .last("limit 1")) != null;
    }

    public java.util.List<String> getDataUsers(String userId) {
        UserConfigValue dataUserRow = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, userId)
                .eq(UserConfigValue::getType, TYPE_NSO)
                .eq(UserConfigValue::getKeyName, KEY_DATA_USER)
                .last("limit 1"));
        if (dataUserRow == null || dataUserRow.getValueName() == null || dataUserRow.getValueName().isBlank()) {
            return java.util.List.of("0");
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : dataUserRow.getValueName().split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        return list.isEmpty() ? java.util.List.of("0") : list;
    }

    /**
     * 直接按 Android dataUser 从 cookie token-provider 取一把 token(不走 per-userId DB 缓存,
     * token-provider 自身 60s 缓存,多账号场景逐个取很轻)。userInfo.id 置为 {@link #accountId}
     * 作为该账号战绩入库的稳定主键(cookie 方案下没有真实 Nintendo id)。
     */
    public SplatoonToken getTokenByDataUser(String dataUser) {
        JSONObject token = nsoTokenProvider.fetchToken(dataUser);
        JSONObject userInfo = new JSONObject();
        userInfo.put("language", LANGUAGE);
        userInfo.put("country", COUNTRY);
        userInfo.put("id", accountId(dataUser));
        return new SplatoonToken(token.getString("gtoken"), token.getString("bulletToken"), userInfo);
    }

    /** 战绩入库的稳定账号主键。cookie 方案下用 Android dataUser 区分账号。 */
    public static String accountId(String dataUser) {
        return "nso-" + dataUser;
    }

    private Map<String, UserConfigValue> loadAll(String userId) {
        Map<String, UserConfigValue> map = new HashMap<>();
        for (String key : new String[]{KEY_USER_INFO, KEY_WEB_SERVICE_TOKEN, KEY_BULLET_TOKEN}) {
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
