package com.bb.bot.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.util.LocalCacheUtils;
import com.bb.bot.util.RestUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任天堂api调用工具
 */
@Slf4j
@Component
public class NsoApiCaller {
    @Autowired
    private RestUtils restUtils;

    /**
     * 获取nso的app版本
     */
    public String getNsoAppVersion() {
        //从缓存获取版本
        String nsoAppVersion = LocalCacheUtils.getCacheObject("nso_app_version");

        if (StringUtils.isBlank(nsoAppVersion)) {
            //如果为空，调用接口获取
            String response = restUtils.get("https://apps.apple.com/us/app/nintendo-switch-online/id1234806557", String.class);
            Document document = Jsoup.parse(response);
            Elements elementsByClass = document.getElementsByClass("whats-new__latest__version");
            nsoAppVersion = elementsByClass.get(0).text().replaceAll("Version ", "");
            log.info("获取到nso的app版本: " + nsoAppVersion);

            //设置为缓存
            LocalCacheUtils.setCacheObject("nso_app_version", nsoAppVersion, 1, ChronoUnit.DAYS);
        }

        return nsoAppVersion;
    }

    /**
     * 获取ns账号页面信息
     * 返回结构参考
     * {"status": 0, "result": {"id": 5176361572663296, "nsaId": "de6c20dde69eb898", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/ef727c2989bf9968", "name": "misubb", "supportId": "1602-7221-3113-3377-5512-4", "isChildRestricted": false, "etag": "\"70542aa517f40189\"", "links": {"nintendoAccount": {"membership": {"active": {"active": true}}}, "friendCode": {"regenerable": true, "regenerableAt": 1592627596, "id": "3831-1801-7129"}}, "permissions": {"presence": "FRIENDS"}, "presence": {"state": "OFFLINE", "updatedAt": 0, "logoutAt": 0, "game": {}}}, "correlationId": "2e9106b9-5b726753"}
     */
    public JSONObject getNsAccountInfo(String webAccessToken) {
        return callNsApi(webAccessToken, "https://api-lp1.znc.srv.nintendo.net/v3/User/ShowSelf");
    }

    /**
     * 获取好友列表
     * 返回结构参考
     * {"status": 0, "result": {"friends": [{"id": 5208132633427968, "nsaId": "7f379d1d2f64916d", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/26c4b209e679dd9d", "name": "|\u03c9\u00f3)\u266amisu", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1667927279, "presence": {"state": "OFFLINE", "updatedAt": 1706633311, "logoutAt": 1706633311, "game": {}}}, {"id": 4848313857146880, "nsaId": "414b671d04f245e9", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/3559f4f42b91805a", "name": "Qiu.", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1691938539, "presence": {"state": "OFFLINE", "updatedAt": 1706651983, "logoutAt": 1706627920, "game": {}}}, {"id": 6660337101471744, "nsaId": "51202829fa08d6db", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/6e354fb4be078c03", "name": "\u307e\u3044\u3054", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1677406937, "presence": {"state": "OFFLINE", "updatedAt": 1706627699, "logoutAt": 1706627699, "game": {}}}, {"id": 6069054015471616, "nsaId": "99afe2bc1291ae3a", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/1d946f16f062b54b", "name": "Aria", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1667226737, "presence": {"state": "OFFLINE", "updatedAt": 1706627990, "logoutAt": 1706552654, "game": {}}}, {"id": 5528201567174656, "nsaId": "e1fbaa85ee5e0459", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/b8cf6ed28efd708a", "name": ":-o", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1701957153, "presence": {"state": "OFFLINE", "updatedAt": 1706678405, "logoutAt": 1705822686, "game": {}}}, {"id": 4906973372121088, "nsaId": "91a4d9e187fd8f09", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/2b5609a9900be1c8", "name": "\u30e2\u30ab\u3000\u30b8\u30e5\u30fc\u30b9", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1663937965, "presence": {"state": "OFFLINE", "updatedAt": 1705595408, "logoutAt": 1705160310, "game": {}}}, {"id": 5686681083379712, "nsaId": "806b3bb098fc3e20", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/a22be9b99b6b4ef3", "name": "m\u00edy\u0113:3", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1678541264, "presence": {"state": "OFFLINE", "updatedAt": 1705152623, "logoutAt": 1705152623, "game": {}}}, {"id": 5080259373236224, "nsaId": "4a5a54fa61771841", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/8a371bde24f697c7", "name": "xinsu", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1677509819, "presence": {"state": "OFFLINE", "updatedAt": 1706622752, "logoutAt": 1704729935, "game": {}}}, {"id": 4695393681309696, "nsaId": "b4ed6096718b683e", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/d741792e426648d9", "name": "= =", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1686327775, "presence": {"state": "OFFLINE", "updatedAt": 1706678405, "logoutAt": 1704712770, "game": {}}}, {"id": 5766165326462976, "nsaId": "0d1807eaceded8ef", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/41fd062a527c2f94", "name": "Kelvin", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1661588403, "presence": {"state": "OFFLINE", "updatedAt": 1706651983, "logoutAt": 1700324545, "game": {}}}, {"id": 0, "nsaId": "79051aaf0733cd61", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/8c03417f458b16b9", "name": "xinsu", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": false, "friendCreatedAt": 1661608071, "presence": {"state": "OFFLINE", "updatedAt": 1704730088, "logoutAt": 1696085116, "game": {}}}, {"id": 0, "nsaId": "0a76c0c2ad007418", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/ae31f86817c442e0", "name": ">\u00f4<!!", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": false, "friendCreatedAt": 1661588284, "presence": {"state": "OFFLINE", "updatedAt": 1706522126, "logoutAt": 1661617402, "game": {}}}, {"id": 0, "nsaId": "3a348492453d5b90", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/899b9b79f5addfb3", "name": "xg", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": false, "friendCreatedAt": 1661596433, "presence": {"state": "OFFLINE", "updatedAt": 1703471624, "logoutAt": 1661601161, "game": {}}}, {"id": 5506376877244416, "nsaId": "3341ea441a4eba0d", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/d8192e0280645fc8", "name": "Sunlight", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1661609670, "presence": {"state": "OFFLINE", "updatedAt": 0, "logoutAt": 0, "game": {}}}, {"id": 6671923474857984, "nsaId": "239a9906fc7fb30d", "imageUri": "https://cdn-image-e0d67c509fb203858ebcb2fe3f88c2aa.baas.nintendo.com/1/d76dfde4774ec48c", "name": "\u307f\u304f\u308b", "isFriend": true, "isFavoriteFriend": false, "isServiceUser": true, "friendCreatedAt": 1663941752, "presence": {"state": "OFFLINE", "updatedAt": 0, "logoutAt": 0, "game": {}}}]}, "correlationId": "24f739a1-63b18b73"}
     */
    public JSONObject getNsFriendList(String webAccessToken) {
        return callNsApi(webAccessToken, "https://api-lp1.znc.srv.nintendo.net/v3/Friend/List");
    }

    /**
     * 调用ns层的api
     */
    public JSONObject callNsApi(String accessToken, String url) {
        HttpHeaders headers = getAccessTokenHead(accessToken);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("id", UUID.randomUUID().toString());
        paramMap.put("parameter", null);

        JSONObject response = restUtils.post(url, headers, paramMap, JSONObject.class);
        return response;
    }

    /**
     * 获取需要使用accessToken的接口的请求头
     */
    public HttpHeaders getAccessTokenHead(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "com.nintendo.znca/" + getNsoAppVersion() + " (Android/7.1.2)");
        headers.set("Accept-Encoding", "gzip");
        headers.set("Accept", "application/json");
        headers.set("Connection", "Keep-Alive");
        headers.set("Host", "api-lp1.znc.srv.nintendo.net");
        headers.set("X-ProductVersion", getNsoAppVersion());
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-Platform", "Android");
        return headers;
    }

    /**
     * 获取登录token
     */
    public JSONObject getLoginToken(JSONObject userInfo, String idToken) {
        JSONObject fResponse = callNsoFApi(idToken, 1, userInfo.getString("id"), null);
        String f = fResponse.getString("f");
        String request_id = fResponse.getString("request_id");
        Long timestamp = fResponse.getLong("timestamp");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Host", "api-lp1.znc.srv.nintendo.net");
        headers.set("User-Agent", "com.nintendo.znca/" + getNsoAppVersion() + " (Android/7.1.2)");
        headers.set("X-Platform", "Android");
        headers.set("X-ProductVersion", getNsoAppVersion());
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Content-Length", String.valueOf(990 + f.getBytes(StandardCharsets.UTF_8).length));
        headers.set("Connection", "Keep-Alive");
        headers.set("Accept-Encoding", "gzip");

        Map<String, Object> subParamMap = new HashMap<>();
        subParamMap.put("language", userInfo.getString("language"));
        subParamMap.put("naCountry", userInfo.getString("country"));
        subParamMap.put("naBirthday", userInfo.getString("birthday"));
        subParamMap.put("naIdToken", idToken);
        subParamMap.put("f", f);
        subParamMap.put("requestId", request_id);
        subParamMap.put("timestamp", timestamp);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("parameter", subParamMap);

        JSONObject webResponse = restUtils.post("https://api-lp1.znc.srv.nintendo.net/v3/Account/Login", headers, paramMap, JSONObject.class);
        return webResponse;
    }

    /**
     * 获取f码
     */
    public JSONObject callNsoFApi(String token, Integer step, String userId, String coralUserId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "s3s/" + getNsoAppVersion());
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("X-znca-Platform", "Android");
        headers.set("X-znca-Version", getNsoAppVersion());

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("token", token);
        paramMap.put("hash_method", step);
        paramMap.put("na_id", userId);

        if (step == 2) {
            paramMap.put("coral_user_id", coralUserId);
        }

        return restUtils.post("https://api.imink.app/f", headers, paramMap, JSONObject.class);
    }

    /**
     * 获取登录用户信息
     */
    public JSONObject getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "NASDKAPI; Android");
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Host", "api.accounts.nintendo.com");
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        headers.set("Connection", "Keep-Alive");
        headers.set("Accept-Encoding", "gzip");
        JSONObject userInfo = restUtils.get("https://api.accounts.nintendo.com/2.0.0/users/me", headers, JSONObject.class);
        return userInfo;
    }

    /**
     * 获取用户token
     */
    public JSONObject getUserToken(String sessionToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 7.1.2)");
        headers.set("Host", "accounts.nintendo.com");
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        headers.set("Connection", "Keep-Alive");
        headers.set("Accept-Encoding", "gzip");

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("client_id", "71b963c1b7b6d119");
        paramMap.put("session_token", sessionToken);
        paramMap.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer-session-token");

        String response = restUtils.post("https://accounts.nintendo.com/connect/1.0.0/api/token", headers, paramMap, String.class);

        JSONObject gToken = JSON.parseObject(response);
        return gToken;
    }

    /**
     * 获取session_token
     */
    public String getSessionToken(String loginInAnswer, String authCodeVerifier) {
        String authCode = findAuthCode(loginInAnswer);
        System.out.println(authCode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "OnlineLounge/" + getNsoAppVersion() + " NASDKAPI Android");
        headers.set("Accept-Language", "en-US");
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/x-www-form-urlencoded");
        headers.set("Content-Length", "540");
        headers.set("Host", "accounts.nintendo.com");
        headers.set("Connection", "Keep-Alive");
        headers.set("Accept-Encoding", "gzip");

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("client_id", "71b963c1b7b6d119");
        paramMap.add("session_token_code", authCode);
        paramMap.add("session_token_code_verifier", authCodeVerifier);

        JSONObject sessionResponse = restUtils.postForForm("https://accounts.nintendo.com/connect/1.0.0/api/session_token", headers, paramMap, JSONObject.class);
        return sessionResponse.getString("session_token");
    }

    /**
     * 获取需要用户自己到浏览器登录的url
     */
    public String getUserLoginInUrl(String authState, String authCodeChallenge) {
        String url = UriComponentsBuilder.fromUriString(/*"https://accounts.nintendo.com" + */ "/connect/1.0.0/authorize")
                .queryParam("state", authState)
                .queryParam("redirect_uri", "npf71b963c1b7b6d119://auth")
                .queryParam("client_id", "71b963c1b7b6d119")
                .queryParam("scope", "openid user user.birthday user.mii user.screenName")
                .queryParam("response_type", "session_token_code")
                .queryParam("session_token_code_challenge", authCodeChallenge)
                .queryParam("session_token_code_challenge_method", "S256")
                .queryParam("theme", "login_form")
                .build().encode().toString();
        log.info("登录url：" + url);
        return url;
    }

    /**
     * 通过正则获取授权url内部的认证码
     */
    private String findAuthCode(String useAccountUrl) {
        // Define the regular expression pattern
        String regex = "de=(.*?)&";

        // Create a Pattern object
        Pattern pattern = Pattern.compile(regex);

        // Create a Matcher object
        Matcher matcher = pattern.matcher(useAccountUrl);

        // Check if the pattern is found
        if (matcher.find()) {
            // Get the captured group (de= value)
            String deValue = matcher.group(1);

            // Print the captured value
            return deValue;
        } else {
            return "";
        }
    }

    /**
     * 将字符串进行sha256类型的哈希编码
     */
    @SneakyThrows
    public static byte[] hashEncode(String data){
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashValue = digest.digest(data.getBytes());
        return hashValue;
    }

    /**
     * 随机一个指定长度的byte数组
     */
    @SneakyThrows
    public static byte[] getRandom(int number) {
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();

        // Generate a byte array with random bytes
        byte[] randomBytes = new byte[number];
        secureRandom.nextBytes(randomBytes);

        return randomBytes;
    }

    /**
     * 编码成Url安全的base64字符串
     */
    public static String safeUrlBase64Encode(byte[] data){
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
