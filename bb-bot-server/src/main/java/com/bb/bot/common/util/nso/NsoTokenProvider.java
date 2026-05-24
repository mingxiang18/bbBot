package com.bb.bot.common.util.nso;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 通过真机 NSO cookie 方案获取 SplatNet token,替代 2024 年中失效的 imink f-API 链。
 *
 * token 来源:worker 上的 nso-token-server。它用 root 读手机 NSO 的 WebView cookie
 * (_gtoken)再换 bulletToken,全程不注入 NSO 进程,不触发 Pairip 反调试。
 * 背景与验证过程见 k8s-nso/NSO-COOKIE-METHOD.md。
 */
@Slf4j
@Component
public class NsoTokenProvider {
    @Autowired
    private RestUtils restUtils;

    @Value("${nso.tokenProviderUrl:http://192.168.50.227:18080/token}")
    private String tokenProviderUrl;

    /**
     * 从 token 服务拿 SplatNet token。
     *
     * @param dataUser 多账号时对应不同 Android /data/user/{N};单号传 "0"
     * @return {gtoken, bulletToken, webViewVer}
     */
    public JSONObject fetchToken(String dataUser) {
        String url = tokenProviderUrl + "?dataUser=" + dataUser;
        JSONObject resp = restUtils.get(url, JSONObject.class);
        if (resp == null || resp.getString("gtoken") == null || resp.getString("bulletToken") == null) {
            throw new RuntimeException("NSO token provider 返回无效: " + resp);
        }
        return resp;
    }
}
