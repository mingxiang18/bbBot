package com.bb.bot.common.util;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.util.LocalCacheUtils;
import com.bb.bot.util.RestClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 斯普拉遁3api调用工具
 * @author ren
 */
@Slf4j
@Component
public class Splatoon3ApiCaller {

    public static final String SPLATNET3_URL = "https://api.lp1.av5ja.srv.nintendo.net";
    public static final String GRAPHQL_URL = SPLATNET3_URL + "/api/graphql";
    public static final UUID S3S_NAMESPACE = UUID.fromString("b3a2dbf5-2c09-4792-b78c-00b548b70aeb");
    //web版本号获取正则
    private static final Pattern WEB_VERSION_PATTERN = Pattern.compile("\\b(?<revision>[0-9a-f]{40})\\b[\\S]*?void 0[\\S]*?\"revision_info_not_set\"\\}`,.*?=`(?<version>\\d+\\.\\d+\\.\\d+)-");

    private static final String[] SUPPORTED_KEYS = {
            "ignore_private",
            "ignore_private_jobs",
            "app_user_agent",
            "force_uploads",
            "errors_pass_silently",
            "old_export_format"
    };

    private static final Map<String, String> translateRid = new HashMap<>();

    static {
        translateRid.put("HomeQuery", "51fc56bbf006caf37728914aa8bc0e2c86a80cf195b4d4027d6822a3623098a8");
        translateRid.put("LatestBattleHistoriesQuery", "b24d22fd6cb251c515c2b90044039698aa27bc1fab15801d83014d919cd45780");
        translateRid.put("RegularBattleHistoriesQuery", "2fe6ea7a2de1d6a888b7bd3dbeb6acc8e3246f055ca39b80c4531bbcd0727bba");
        translateRid.put("BankaraBattleHistoriesQuery", "9863ea4744730743268e2940396e21b891104ed40e2286789f05100b45a0b0fd");
        translateRid.put("PrivateBattleHistoriesQuery", "fef94f39b9eeac6b2fac4de43bc0442c16a9f2df95f4d367dd8a79d7c5ed5ce7");
        translateRid.put("XBattleHistoriesQuery", "eb5996a12705c2e94813a62e05c0dc419aad2811b8d49d53e5732290105559cb");
        translateRid.put("VsHistoryDetailQuery", "f893e1ddcfb8a4fd645fd75ced173f18b2750e5cfba41d2669b9814f6ceaec46");
        translateRid.put("CoopHistoryQuery", "0f8c33970a425683bb1bdecca50a0ca4fb3c3641c0b2a1237aedfde9c0cb2b8f");
        translateRid.put("CoopHistoryDetailQuery", "42262d241291d7324649e21413b29da88c0314387d8fdf5f6637a2d9d29954ae");
        translateRid.put("MyOutfitCommonDataEquipmentsQuery", "45a4c343d973864f7bb9e9efac404182be1d48cf2181619505e9b7cd3b56a6e8");
        translateRid.put("FriendsList", "ea1297e9bb8e52404f52d89ac821e1d73b726ceef2fd9cc8d6b38ab253428fb3");
        translateRid.put("HistorySummary", "0a62c0152f27c4218cf6c87523377521c2cff76a4ef0373f2da3300079bf0388");
        translateRid.put("TotalQuery", "2a9302bdd09a13f8b344642d4ed483b9464f20889ac17401e993dfa5c2bb3607");
        translateRid.put("XRankingQuery", "a5331ed228dbf2e904168efe166964e2be2b00460c578eee49fc0bc58b4b899c");
        translateRid.put("ScheduleQuery", "9b6b90568f990b2a14f04c25dd6eb53b35cc12ac815db85ececfccee64215edd");
        translateRid.put("StageRecordsQuery", "c8b31c491355b4d889306a22bd9003ac68f8ce31b2d5345017cdd30a2c8056f3");
        translateRid.put("EventBattleHistoriesQuery", "e47f9aac5599f75c842335ef0ab8f4c640e8bf2afe588a3b1d4b480ee79198ac");
        translateRid.put("EventListQuery", "875a827a6e460c3cd6b1921e6a0872d8b95a1fce6d52af79df67734c5cc8b527");
        translateRid.put("EventBoardQuery", "ad4097d5fb900b01f12dffcb02228ef6c20ddbfba41f0158bb91e845335c708e");
        translateRid.put("CoopPagerLatestCoopQuery", "bc8a3d48e91d5d695ef52d52ae466920670d4f4381cb288cd570dc8160250457");
        translateRid.put("PagerLatestVsDetailQuery", "73462e18d464acfdf7ac36bde08a1859aa2872a90ed0baed69c94864c20de046");
        translateRid.put("CoopStatistics", "56f989a59643642e0799c90d3f6d0457f5f5f72d4444dfae87043c4a23d13043");
        translateRid.put("XRanking500Query", "90932ee3357eadab30eb11e9d6b4fe52d6b35fde91b5c6fd92ba4d6159ea1cb7");
    }

    @Autowired
    private RestClient restClient;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    /**
     * 斯普拉顿3最近对战查询
     */
    public JSONObject getRecentBattles(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("LatestBattleHistoriesQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 斯普拉顿3最新一局对战id查询
     */
    public JSONObject getLastOneBattle(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("PagerLatestVsDetailQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 蛮颓对战查询
     */
    public JSONObject getBankaraBattles(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("BankaraBattleHistoriesQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 涂地对战查询
     */
    public JSONObject getRegularBattles(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("RegularBattleHistoriesQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 活动对战查询
     */
    public JSONObject getEventBattles(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("EventBattleHistoriesQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * x对战查询
     */
    public JSONObject getXBattleHistories(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("XBattleHistoriesQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * x排行榜top1查询
     */
    public JSONObject getXRanking(String bulletToken, String webServiceToken, JSONObject userInfo, String area) {
        Object data = genGraphqlBody(translateRid.get("XRankingQuery"), "region", area);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * x排行榜500强查询
     */
    public JSONObject getXRanking500(String bulletToken, String webServiceToken, JSONObject userInfo, String topId) {
        Object data = genGraphqlBody(translateRid.get("XRanking500Query"), "id", topId);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 提供data数据进行自定义查询
     */
    public JSONObject getCustomData(String bulletToken, String webServiceToken, JSONObject userInfo, Object data) {
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 测试内容查询
     */
    public JSONObject getTest(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("TotalQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 指定对战id查询细节
     */
    public JSONObject getBattleDetail(String bulletToken, String webServiceToken, JSONObject userInfo, String battleId) {
        Object data = genGraphqlBody(translateRid.get("VsHistoryDetailQuery"), "vsResultId", battleId);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 打工历史
     */
    public JSONObject getCoops(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("CoopHistoryQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 指定打工id查询细节
     */
    public JSONObject getCoopDetail(String bulletToken, String webServiceToken, JSONObject userInfo, String battleId) {
        Object data = genGraphqlBody(translateRid.get("CoopHistoryDetailQuery"), "coopHistoryDetailId", battleId);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 打工统计数据(全部boss击杀数量)
     */
    public JSONObject getCoopStatistics(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("CoopStatistics"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 主页 - 历史 页面 全部分类数据
     */
    public JSONObject getHistorySummary(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("HistorySummary"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * nso没有这个页面，统计比赛场数
     */
    public JSONObject getTotalQuery(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("TotalQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 获取活动条目
     */
    public JSONObject getEventList(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("EventListQuery"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 获取活动内容
     */
    public JSONObject getEventItems(String bulletToken, String webServiceToken, JSONObject userInfo, String topId) {
        Object data = genGraphqlBody(translateRid.get("EventBoardQuery"), "eventMatchRankingPeriodId", topId);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 获取sp3好友
     */
    public JSONObject getFriends(String bulletToken, String webServiceToken, JSONObject userInfo) {
        Object data = genGraphqlBody(translateRid.get("FriendsList"), null, null);
        JSONObject response = callSplatoon3Api(data, bulletToken, webServiceToken, userInfo);
        return response;
    }

    /**
     * 调用斯普拉顿3服务的api
     */
    public JSONObject callSplatoon3Api(Object data, String bulletToken, String webServiceToken, JSONObject userInfo) {
        HttpHeaders headers = getBulletHead(bulletToken, webServiceToken, userInfo);

        JSONObject response = restClient.post(GRAPHQL_URL, headers, data, JSONObject.class);
        return response;
    }

    /**
     * 获取需要使用的BulletToken的接口的请求头
     */
    public HttpHeaders getBulletHead(String bulletToken, String webServiceToken, JSONObject userInfo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + bulletToken);
        headers.set("Accept-Language", userInfo.getString("language"));
        headers.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        headers.set("X-Web-View-Ver", getWebViewVersion());
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "*/*");
        headers.set("Origin", Splatoon3ApiCaller.SPLATNET3_URL);
        headers.set("X-Requested-With", "com.nintendo.znca");
        headers.set("Referer", Splatoon3ApiCaller.SPLATNET3_URL + "/?lang=" + userInfo.getString("language") +
                "&na_country=" + userInfo.getString("country") +
                "&na_lang=" + userInfo.getString("language"));
        headers.set("Accept-Encoding", "gzip, deflate");
        headers.set("Cookie", "_gtoken=" + webServiceToken);
        return headers;
    }

    /**
     * 刷新token信息
     */
    public String refreshToken(String sessionToken) {
        //获取用户token
        JSONObject userToken = nsoApiCaller.getUserToken(sessionToken);
        String accessToken = userToken.getString("access_token");
        String idToken = userToken.getString("id_token");

        //获取用户账号信息
        JSONObject userInfo = nsoApiCaller.getUserInfo(accessToken);

        //获取登录token
        JSONObject webLoginToken = nsoApiCaller.getLoginToken(userInfo, idToken);
        String webAccessToken = webLoginToken.getJSONObject("result").getJSONObject("webApiServerCredential").getString("accessToken");
        String coralUserId  = webLoginToken.getJSONObject("result").getJSONObject("user").getString("id");

        //获取斯普拉顿3 web服务token
        String webServiceToken = getWebServiceToken(userInfo, webAccessToken, coralUserId);

        //获取操作token
        String bulletToken = getBulletToken(webServiceToken, userInfo);

        return bulletToken;
    }

    /**
     * 获取子弹（不知道怎么翻译）token
     */
    public String getBulletToken(String webServiceToken, JSONObject userInfo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Length", "0");
        headers.set("Content-Type", "application/json");
        headers.set("Accept-Language", userInfo.getString("language"));
        headers.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        headers.set("X-Web-View-Ver", getWebViewVersion());
        headers.set("X-NACOUNTRY", userInfo.getString("country"));
        headers.set("Accept", "*/*");
        headers.set("Origin", "https://api.lp1.av5ja.srv.nintendo.net");
        headers.set("X-Requested-With", "com.nintendo.znca");
        headers.set("Cookie", "_dnt=1;_gtoken=" + webServiceToken);

        JSONObject bulletResponse = restClient.post("https://api.lp1.av5ja.srv.nintendo.net/api/bullet_tokens", headers, new HashMap<String, Object>(), JSONObject.class);

        return bulletResponse.getString("bulletToken");
    }

    /**
     * 获取Web服务的调用token
     */
    public String getWebServiceToken(JSONObject userInfo, String webAccessToken, String coralUserId) {
        JSONObject fResponse2 = nsoApiCaller.callNsoFApi(webAccessToken, 2, userInfo.getString("id"), coralUserId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Platform", "Android");
        headers.set("X-ProductVersion", nsoApiCaller.getNsoAppVersion());
        headers.set("Authorization", "Bearer " + webAccessToken);
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Content-Length", "391");
        headers.set("Accept-Encoding", "gzip");
        headers.set("User-Agent", "com.nintendo.znca/" + nsoApiCaller.getNsoAppVersion() + " (Android/7.1.2)");

        Map<String, Object> subParamMap = new HashMap<>();
        subParamMap.put("id", "4834290508791808");
        subParamMap.put("registrationToken", webAccessToken);
        subParamMap.put("f", fResponse2.getString("f"));
        subParamMap.put("requestId", fResponse2.getString("request_id"));
        subParamMap.put("timestamp", fResponse2.getLong("timestamp"));

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("parameter", subParamMap);

        JSONObject webAuthResponse = restClient.post("https://api-lp1.znc.srv.nintendo.net/v2/Game/GetWebServiceToken", headers, paramMap, JSONObject.class);
        String web_service_token = webAuthResponse.getJSONObject("result").getString("accessToken");
        return web_service_token;
    }

    /**
     * 获取斯普拉顿Web浏览界面的版本
     */
    public String getWebViewVersion() {
        //从缓存获取版本
        String webViewVersion = LocalCacheUtils.getCacheObject("nso_web_view_version");

        if (StringUtils.isBlank(webViewVersion)) {
            //----------------------调用html页面，获取javascript文件获取地址-----------------------
            HttpHeaders headers = new HttpHeaders();
            headers.set("Upgrade-Insecure-Requests", "1");
            headers.set("Accept", "*/*");
            headers.set("DNT", "1");
            headers.set("X-AppColorScheme", "DARK");
            headers.set("X-Requested-With", "com.nintendo.znca");
            headers.set("Sec-Fetch-Site", "none");
            headers.set("Sec-Fetch-Mode", "navigate");
            headers.set("Sec-Fetch-User", "?1");
            headers.set("Sec-Fetch-Dest", "document");
            headers.set("Cookie", "_dnt=1");
            String webHtml = restClient.get("https://api.lp1.av5ja.srv.nintendo.net", headers, String.class);
            Document document = Jsoup.parse(webHtml);
            // 使用选择器查找包含'static'字符串的script元素
            Elements mainJs = document.select("script[src*=static]");

            if (mainJs == null) {
                throw new RuntimeException("无法找到script元素的src地址");
            }

            // 如果找到了script元素，输出它的src属性值
            String mainJsUrl = mainJs.attr("src");


            //----------------------调用javascript文件获取地址，获取web端版本号-----------------------
            headers = new HttpHeaders();
            headers.set("Accept", "*/*");
            headers.set("X-Requested-With", "com.nintendo.znca");
            headers.set("Sec-Fetch-Site", "same-origin");
            headers.set("Sec-Fetch-Mode", "no-cors");
            headers.set("Sec-Fetch-Dest", "script");
            headers.set("Referer", "https://api.lp1.av5ja.srv.nintendo.net");
            headers.set("Cookie", "_dnt=1");
            String mainJsBodyText = restClient.get("https://api.lp1.av5ja.srv.nintendo.net" + mainJsUrl, headers, String.class);

            // 在 main_js_body_text 中搜索版本号正则匹配项
            Matcher matcher = WEB_VERSION_PATTERN.matcher(mainJsBodyText);
            String revision = null;
            String version = null;
            // 如果找到匹配项
            if (matcher.find()) {
                revision = matcher.group("revision");
                version = matcher.group("version");
            } else {
                // 如果没有找到匹配项
                throw new RuntimeException("无法找到指定web版本匹配项");
            }

            // 根据需要进行输出或其他处理
            webViewVersion = version + "-" + revision.substring(0, 8);

            //设置为缓存
            LocalCacheUtils.setCacheObject("nso_web_view_version", webViewVersion, 1, ChronoUnit.DAYS);
        }
        return webViewVersion;
    }

    public static String setNoun(String which) {
        if ("both".equals(which)) {
            return "battles/jobs";
        } else if ("salmon".equals(which)) {
            return "jobs";
        } else {
            return "battles";
        }
    }

    public static String b64d(String string) {
        byte[] decodedBytes = Base64.getDecoder().decode(string);
        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

        // Additional string replacements as needed
        decodedString = decodedString.replace("VsStage-", "")
                .replace("VsMode-", "")
                .replace("CoopStage-", "")
                .replace("CoopGrade-", "")
                .replace("CoopEnemy-", "")
                .replace("CoopEventWave-", "")
                .replace("CoopUniform-", "")
                .replace("SpecialWeapon-", "");

        if (decodedString.startsWith("Weapon-")) {
            decodedString = decodedString.replace("Weapon-", "");
            if (decodedString.length() == 5 && decodedString.startsWith("2") && decodedString.endsWith("900")) {
                return "";
            }
        }

        return decodedString;
    }

    public static long epochTime(String timeString) {
        Instant instant = Instant.parse(timeString);
        return instant.getEpochSecond();
    }

    /**
     * 封装splatoon3的web界面api的请求体
     */
    public static Map<String, Object> genGraphqlBody(String sha256Hash, String varName, String varValue) {
        Map<String, Object> greatPassage = new HashMap<>();
        Map<String, Object> extensions = new HashMap<>();
        Map<String, Object> persistedQuery = new HashMap<>();
        Map<String, Object> variables = new HashMap<>();

        persistedQuery.put("sha256Hash", sha256Hash);
        persistedQuery.put("version", 1);
        extensions.put("persistedQuery", persistedQuery);
        greatPassage.put("extensions", extensions);

        if (varName != null && varValue != null) {
            variables.put(varName, varValue);
            greatPassage.put("variables", variables);
        }

        return greatPassage;
    }
}
