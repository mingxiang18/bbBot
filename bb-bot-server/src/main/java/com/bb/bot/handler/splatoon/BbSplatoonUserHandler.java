package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.ConfigKeys;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.nso.Splatoon3ApiCaller;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.common.util.nso.SplatoonTokenManager;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 喷喷获取已登录的用户事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "斯普拉遁3用户数据（需登录）")
public class BbSplatoonUserHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private com.bb.bot.common.util.BbReplies bbReplies;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private ISplatoonCoopRecordsService coopRecordService;

    @Autowired
    private ISplatoonCoopUserDetailService coopUserDetailService;

    @Autowired
    private ISplatoonBattleRecordService battleRecordService;

    @Autowired
    private ISplatoonBattleUserDetailService battleUserDetailService;

    @Autowired
    private SplatoonTokenManager splatoonTokenManager;

    @Autowired
    private AiAgentAuthService aiAgentAuthService;

    @Autowired
    private com.bb.bot.handler.splatoon.render.CoopPointRenderer coopPointRenderer;

    @Autowired
    private com.bb.bot.handler.splatoon.render.SplatoonRecordRenderer splatoonRecordRenderer;

    @Autowired
    private com.bb.bot.handler.splatoon.render.SplatoonHtmlRenderer splatoonHtmlRenderer;

    @Autowired
    private com.bb.bot.database.splatoon.service.ISplatoonCoopWaveDetailService coopWaveDetailService;

    @Autowired
    private com.bb.bot.database.splatoon.service.ISplatoonCoopEnemyDetailService coopEnemyDetailService;

    /** 账号编号→Android dataUser 映射(账号1=主NSO user0, 账号2=应用双开user999...)。代码默认值,无需写 yml。 */
    @Value("${nso.accountMap:1:0,2:999}")
    private String accountMap;

    /**
     * 自动上传喷喷记录
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"自动上传喷喷记录", "/自动上传喷喷记录", "关闭自动上传喷喷记录", "/关闭自动上传喷喷记录"}, name = "自动上传喷喷记录")
    public void autoUploadRecordsConfig(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        //获取用户配置
        UserConfigValue userConfigValue = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                .eq(UserConfigValue::getType, ConfigKeys.NSO_TYPE)
                .eq(UserConfigValue::getKeyName, ConfigKeys.AUTO_UPLOAD));

        //判断是开启还是关闭
        String openFlag = "0";
        if (!bbReceiveMessage.getMessage().contains("关闭")) {
            openFlag = "1";
        }

        //保存配置到数据库
        if (userConfigValue == null) {
            userConfigValue = new UserConfigValue();
            userConfigValue.setUserId(bbReceiveMessage.getUserId());
            userConfigValue.setType(ConfigKeys.NSO_TYPE);
            userConfigValue.setKeyName(ConfigKeys.AUTO_UPLOAD);
            userConfigValue.setValueName(openFlag);
            userConfigValueService.save(userConfigValue);
        }else {
            userConfigValue.setValueName(openFlag);
            userConfigValueService.updateById(userConfigValue);
        }

        //回复消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent("已" + ("1".equals(openFlag) ? "开启" : "关闭") + "自动上传记录"))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * owner 绑定 bbBot 用户到一个或多个 NSO 账号(账号编号→Android dataUser)。
     * cookie 方案下 token 来自 owner 的几台真机账号,绑定关系只能 owner 设置。
     * 格式: 绑定喷喷账号 <编号...> [@某人];可绑多个,如"绑定喷喷账号 1 2";不 @ 则绑发送者自己。
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?绑定喷喷账号"}, name = "绑定喷喷账号")
    public void bindSplatoonAccount(BbReceiveMessage bbReceiveMessage) {
        BbSendMessage reply = new BbSendMessage(bbReceiveMessage);
        //owner 校验(复用 aiAgent.owners 配置)
        if (!aiAgentAuthService.isOwner(bbReceiveMessage.getUserId())) {
            reply.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("仅 owner 可绑定喷喷账号")));
            bbMessageApi.sendMessage(reply);
            return;
        }
        //取关键词后的前导纯数字 token 作为账号编号(遇到非数字如 @某人 即停止,避免误吞 at 的 id)
        String rest = bbReceiveMessage.getMessage().replaceFirst(".*?绑定喷喷账号", "").trim();
        List<String> accountNos = new ArrayList<>();
        for (String tok : rest.split("\\s+")) {
            if (tok.matches("\\d+")) {
                accountNos.add(tok);
            } else {
                break;
            }
        }
        if (accountNos.isEmpty()) {
            reply.setMessageList(Arrays.asList(
                    BbMessageContent.buildTextContent("格式：绑定喷喷账号 <编号...> [@某人]，如：绑定喷喷账号 1 或 绑定喷喷账号 1 2")));
            bbMessageApi.sendMessage(reply);
            return;
        }
        //账号编号→dataUser
        List<String> dataUsers = new ArrayList<>();
        for (String accountNo : accountNos) {
            String dataUser = null;
            for (String pair : accountMap.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2 && kv[0].trim().equals(accountNo)) {
                    dataUser = kv[1].trim();
                    break;
                }
            }
            if (dataUser == null) {
                reply.setMessageList(Arrays.asList(
                        BbMessageContent.buildTextContent("账号编号 " + accountNo + " 未配置(见 nso.accountMap)")));
                bbMessageApi.sendMessage(reply);
                return;
            }
            if (!dataUsers.contains(dataUser)) {
                dataUsers.add(dataUser);
            }
        }
        //被绑用户:@ 的优先,否则 owner 自己
        String targetUserId = bbReceiveMessage.getAtUserList().isEmpty()
                ? bbReceiveMessage.getUserId()
                : bbReceiveMessage.getAtUserList().get(0).getUserId();
        //存 dataUser 映射(逗号分隔多账号),checkAndGetSplatoon3UserToken / 查战绩据此取对应账号 token
        UserConfigValue cfg = new UserConfigValue();
        cfg.setUserId(targetUserId);
        cfg.setType(ConfigKeys.NSO_TYPE);
        cfg.setKeyName(ConfigKeys.DATA_USER);
        cfg.setValueName(String.join(",", dataUsers));
        userConfigValueService.resetUserConfigValue(cfg);
        reply.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("已将用户 " + targetUserId + " 绑定到账号 " + String.join("、", accountNos))));
        bbMessageApi.sendMessage(reply);
    }

    /**
     * 获取喷喷好友列表
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^喷喷好友", "^/喷喷好友"}, name = "获取喷喷好友列表")
    public void getSplatoon3FriendList(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("喷喷好友(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        Integer pageNum = 1;
        Integer pageSize = 10;
        Integer pageStart = 0;
        // 如果找到匹配项
        if (matcher.find()) {
            pageNum = Integer.valueOf(matcher.group(1));
        }
        pageStart = (pageNum-1) * pageSize;

        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //调用接口获取数据
        JSONObject friends = splatoon3ApiCaller.getFriends(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        StringBuilder returnMessage = new StringBuilder();
        //拼装好友登录状态
        JSONArray friendMessageList = friends.getJSONObject("data").getJSONObject("friends").getJSONArray("nodes");
        returnMessage.append("好友数：" + friendMessageList.size() + "\n");
        returnMessage.append("页数：" + pageNum + "/" + (friendMessageList.size() % pageSize == 0 ? (friendMessageList.size() / pageSize) : (friendMessageList.size() / pageSize + 1)) + "\n");

        for (int i = pageStart; i < friendMessageList.size() && i < pageStart + pageSize; i++) {
            JSONObject friendMessage = friendMessageList.getJSONObject(i);
            String onlineState = friendMessage.getString("onlineState");
            if ("VS_MODE_FIGHTING".equals(onlineState)) {
                onlineState = "比赛中";
            }else if ("VS_MODE_MATCHING".equals(onlineState)) {
                onlineState = "比赛匹配中";
            }else if ("COOP_MODE_FIGHTING".equals(onlineState)) {
                onlineState = "打工中";
            }else if ("COOP_MODE_MATCHING".equals(onlineState)) {
                onlineState = "打工匹配中";
            }else if ("ONLINE".equals(onlineState)) {
                onlineState = "在线";
            }else if ("OFFLINE".equals(onlineState)) {
                onlineState = "离线";
            }
            returnMessage.append("好友名：【" + friendMessage.getString("nickname") + "】" +
                    ",  " + onlineState + "\n");
        }

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent(returnMessage.toString()))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * SplatNet 概览图。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"喷喷概览", "/喷喷概览"}, name = "喷喷概览")
    public void getSplatoonOverview(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject home = splatoon3ApiCaller.getHome(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        JSONObject historySummary = splatoon3ApiCaller.getHistorySummary(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        JSONObject total = splatoon3ApiCaller.getTotalQuery(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderOverview(home, historySummary, total));
    }

    /**
     * 打工统计图。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"打工统计", "/打工统计"}, name = "打工统计")
    public void getSplatoonCoopStatistics(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject coopStatistics = splatoon3ApiCaller.getCoopStatistics(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderCoopStatistics(coopStatistics));
    }

    /**
     * X 排名总览。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"X排名", "/X排名", "x排名", "/x排名"}, name = "X排名总览")
    public void getSplatoonXRankingHub(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject xRanking = splatoon3ApiCaller.getXRanking(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), "ATLANTIC");
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderXRankingHub(xRanking));
    }

    /**
     * X 排名分模式前十。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?[Xx]排名(区域|塔楼|鱼虎|蛤蜊)$"}, name = "X排名前十")
    public void getSplatoonXRankingTop(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        Matcher matcher = Pattern.compile("^/?[Xx]排名(区域|塔楼|鱼虎|蛤蜊)$").matcher(bbReceiveMessage.getMessage());
        if (!matcher.find()) {
            bbReplies.atText(bbReceiveMessage, "格式：X排名区域 / X排名塔楼 / X排名鱼虎 / X排名蛤蜊");
            return;
        }
        String modeName = matcher.group(1);
        Map<String, String> modeCodes = new HashMap<>();
        modeCodes.put("区域", "Ar");
        modeCodes.put("塔楼", "Lf");
        modeCodes.put("鱼虎", "Gl");
        modeCodes.put("蛤蜊", "Cl");

        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject xRanking = splatoon3ApiCaller.getXRanking(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), "ATLANTIC");
        String seasonId = xRanking.getJSONObject("data").getJSONObject("xRanking").getJSONObject("currentSeason").getString("id");
        if (seasonId == null) {
            bbReplies.atText(bbReceiveMessage, "没拿到当前 X 赛季信息，稍后再试试");
            return;
        }
        String modeCode = modeCodes.get(modeName);
        JSONObject xTop = splatoon3ApiCaller.getXRankingTop(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), modeCode, seasonId, 10);
        JSONArray edges = xTop.getJSONObject("data").getJSONObject("node").getJSONObject("xRanking" + modeCode).getJSONArray("edges");
        JSONArray holders = new JSONArray();
        for (int i = 0; i < edges.size(); i++) {
            holders.add(edges.getJSONObject(i).getJSONObject("node"));
        }
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderXRankingMode(modeName, "ATLANTIC", holders));
    }

    /**
     * 活动比赛榜单。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"活动榜单", "/活动榜单", "活动比赛榜单", "/活动比赛榜单"}, name = "活动比赛榜单")
    public void getSplatoonEventBoard(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject pagination = splatoon3ApiCaller.getEventMatchRankingPagination(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        String periodId = latestEventPeriodId(pagination);
        if (periodId == null) {
            bbReplies.atText(bbReceiveMessage, "当前没有可展示的活动比赛榜单");
            return;
        }
        JSONObject eventBoard = splatoon3ApiCaller.getEventItems(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), periodId);
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderEventBoard(eventBoard.getJSONObject("data").getJSONObject("rankingPeriod")));
    }

    /**
     * 场地记录与装备收藏。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"场地装备", "/场地装备", "舞台装备", "/舞台装备"}, name = "场地装备")
    public void getSplatoonStageGear(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject stageRecords = splatoon3ApiCaller.getStageRecords(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        JSONObject equipments = splatoon3ApiCaller.getMyOutfitCommonDataEquipments(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderStageGear(stageRecords, equipments));
    }

    /**
     * 好友状态图。文本版「喷喷好友」保留原分页行为。
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"喷喷好友图", "/喷喷好友图", "好友状态图", "/好友状态图"}, name = "喷喷好友状态图")
    public void getSplatoonFriendsImage(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject friends = splatoon3ApiCaller.getFriends(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        sendSplatoonImage(bbReceiveMessage, splatoonHtmlRenderer.renderFriends(friends));
    }

    /**
     * 获取打工点数
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"打工点数", "/打工点数"}, name = "打工点数")
    public void getCoopPoint(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        JSONObject userCoopData = splatoon3ApiCaller.getCoops(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        JSONObject coopResult = userCoopData.getJSONObject("data").getJSONObject("coopResult");
        File imageFile = coopPointRenderer.render(coopResult);

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildLocalImageMessageContent(imageFile))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 上传打工记录
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"上传打工记录", "/上传打工记录"}, name = "上传打工记录")
    public void syncCoopRecords(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        //开始上传打工记录
        syncCoopRecords(bbReceiveMessage.getUserId());

        //发送回复消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent("上传打工记录完成"))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 上传打工记录:遍历该用户绑定的每个账号,逐个上传到各自账号名下。
     */
    public void syncCoopRecords(String userId) {
        for (String dataUser : splatoonTokenManager.getDataUsers(userId)) {
            SplatoonTokenManager.SplatoonToken token = splatoonTokenManager.getTokenByDataUser(dataUser);
            syncCoopRecordsForAccount(new TokenInfo(token.webServiceToken(), token.bulletToken(), token.userInfo()));
        }
    }

    /**
     * 上传单个账号的打工记录。
     */
    private void syncCoopRecordsForAccount(TokenInfo tokenInfo) {
        //调用接口获取数据
        JSONObject coops = splatoon3ApiCaller.getCoops(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //获取打工数据里的历史节点
        JSONArray historyNodesArray = coops.getJSONObject("data").getJSONObject("coopResult").getJSONObject("historyGroups").getJSONArray("nodes");

        for (int i = 0; i < historyNodesArray.size(); i++) {
            //获取一个时段的打工信息
            JSONObject coopGroup = historyNodesArray.getJSONObject(i);
            //获取同一个时段的工的所有打工记录
            JSONArray coopRecordNodes = coopGroup.getJSONObject("historyDetails").getJSONArray("nodes");
            for (int j = 0; j < coopRecordNodes.size(); j++) {
                JSONObject coopRecord = coopRecordNodes.getJSONObject(j);
                String appCoopId = coopRecord.getString("id");
                //查询数据库是否已存在记录
                SplatoonCoopRecord record = coopRecordService.getOne(new LambdaQueryWrapper<SplatoonCoopRecord>()
                        .eq(SplatoonCoopRecord::getUserId, userAccountId)
                        .eq(SplatoonCoopRecord::getAppCoopId, appCoopId));

                //如果不存在则开始记录
                if (record == null) {
                    //调用接口获取打工数据详情
                    JSONObject coopDetail = splatoon3ApiCaller.getCoopDetail(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), appCoopId);
                    coopDetail = coopDetail.getJSONObject("data").getJSONObject("coopHistoryDetail");

                    //保存获取到的打工记录详情
                    coopRecordService.saveCoopRecordDetail(userAccountId, coopRecord, coopDetail);
                }

            }
        }
    }

    /**
     * 打工记录
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?打工记录(\\d*)-?(\\d*)"}, name = "打工记录")
    public void getCoopRecords(BbReceiveMessage bbReceiveMessage) {
        sendRecordList(bbReceiveMessage, RecordType.COOP);
    }

    /**
     * 上传对战记录
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"上传对战记录", "/上传对战记录"}, name = "上传对战记录")
    public void syncBattleRecords(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        //开始上传对战记录
        syncBattleRecords(bbReceiveMessage.getUserId());

        //发送回复消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildTextContent("上传对战记录完成"))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 上传对战记录:遍历该用户绑定的每个账号,逐个上传到各自账号名下。
     */
    public void syncBattleRecords(String userId) {
        for (String dataUser : splatoonTokenManager.getDataUsers(userId)) {
            SplatoonTokenManager.SplatoonToken token = splatoonTokenManager.getTokenByDataUser(dataUser);
            syncBattleRecordsForAccount(new TokenInfo(token.webServiceToken(), token.bulletToken(), token.userInfo()));
        }
    }

    /**
     * 上传单个账号的对战记录。
     */
    private void syncBattleRecordsForAccount(TokenInfo tokenInfo) {
        //调用接口获取数据
        JSONObject battles = splatoon3ApiCaller.getRecentBattles(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //获取对战数据里的历史节点
        JSONArray historyNodesArray = battles.getJSONObject("data").getJSONObject("latestBattleHistories").getJSONObject("historyGroups").getJSONArray("nodes");

        for (int i = 0; i < historyNodesArray.size(); i++) {
            //获取一个时段的对战信息
            JSONObject coopGroup = historyNodesArray.getJSONObject(i);
            //获取所有对战记录
            JSONArray battleRecordNodes = coopGroup.getJSONObject("historyDetails").getJSONArray("nodes");
            for (int j = 0; j < battleRecordNodes.size(); j++) {
                JSONObject battleRecord = battleRecordNodes.getJSONObject(j);
                String appBattleId = battleRecord.getString("id");
                //查询数据库是否已存在记录
                SplatoonBattleRecord record = battleRecordService.getOne(new LambdaQueryWrapper<SplatoonBattleRecord>()
                        .eq(SplatoonBattleRecord::getUserId, userAccountId)
                        .eq(SplatoonBattleRecord::getAppBattleId, appBattleId));

                //如果不存在则开始记录
                if (record == null) {
                    //调用接口获取对战数据详情
                    JSONObject battleDetail = splatoon3ApiCaller.getBattleDetail(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), appBattleId);
                    battleDetail = battleDetail.getJSONObject("data").getJSONObject("vsHistoryDetail");

                    //保存获取到的对战记录详情
                    battleRecordService.saveBattleRecordDetail(userAccountId, battleRecord, battleDetail);
                }

            }
        }
    }

    /**
     * 对战记录
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?对战记录(\\d*)-?(\\d*)"}, name = "对战记录")
    public void getBattleRecords(BbReceiveMessage bbReceiveMessage) {
        sendRecordList(bbReceiveMessage, RecordType.BATTLE);
    }

    /**
     * 打工/对战记录列表的统一主流程（合并自原 {@code getCoopRecords}/{@code getBattleRecords} 两份同构逻辑）。
     *
     * <p>流程：绑定校验 → 区间解析（{@link RecordRangeParser}）与分支回复 → 跨账号聚合查询 → HTML 渲染图回复。
     * 区间口径沿用重构前：默认 1-5、跨度上限 20；min/max 取 1..1000（足够大，对正常使用等价于不裁剪）。
     * 打工/对战仅在查询表、用户详情查询与渲染器调用三处不同，由 {@link RecordType} 分支承载，
     * 文本与渲染图与重构前逐一等价。
     */
    private void sendRecordList(BbReceiveMessage bbReceiveMessage, RecordType type) {
        if (!requireBound(bbReceiveMessage)) { return; }

        // 区间解析（默认 1-5、跨度上限 20，min/max=1..1000）
        RecordRangeParser.Result range = RecordRangeParser.parse(
                bbReceiveMessage.getMessage(), type, 1, 5, 1, 1000, 20);
        if (!range.isValid()) {
            // 失败分支：格式错误 / 超上限，文案与重构前一致
            String hint = range.getError() == RecordRangeParser.Error.SPAN_EXCEEDED
                    ? "查询记录超过" + range.getMaxSpan() + "条了，太多啦"
                    : type.getFormatErrorHint();
            bbReplies.atText(bbReceiveMessage, hint);
            return;
        }
        int recordStart = range.getStart();
        int recordEnd = range.getEnd();

        //该用户绑定的全部账号(查战绩跨账号聚合)
        List<String> accountIds = splatoonTokenManager.getDataUsers(bbReceiveMessage.getUserId()).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());

        String limit = "limit " + (recordStart - 1) + "," + (recordEnd - recordStart + 1);

        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();
        //绘制图片(HTML 渲染器)，按类型分支查询 + 渲染；无记录时回提示并返回
        File imageFile;
        if (type == RecordType.COOP) {
            //查询数据库记录
            List<SplatoonCoopRecord> recordList = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                    .in(SplatoonCoopRecord::getUserId, accountIds)
                    .orderByDesc(SplatoonCoopRecord::getPlayedTime)
                    .last(limit));
            //无记录时直接提示(否则下面的 in 空列表会拼出非法 SQL)
            if (recordList.isEmpty()) {
                bbReplies.atText(bbReceiveMessage, type.getEmptyHint());
                return;
            }
            //查询数据库用户详细记录
            List<SplatoonCoopUserDetail> userDetailList = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                    .in(SplatoonCoopUserDetail::getCoopId, recordList.stream().map(splatoonCoopRecord -> splatoonCoopRecord.getId().toString()).collect(Collectors.toList())));
            imageFile = splatoonHtmlRenderer.renderCoopList(recordList, userDetailList);
        } else {
            //查询数据库记录
            List<SplatoonBattleRecord> recordList = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                    .in(SplatoonBattleRecord::getUserId, accountIds)
                    .orderByDesc(SplatoonBattleRecord::getPlayedTime)
                    .last(limit));
            //无记录时直接提示(否则下面的 in 空列表会拼出非法 SQL)
            if (recordList.isEmpty()) {
                bbReplies.atText(bbReceiveMessage, type.getEmptyHint());
                return;
            }
            //查询数据库用户详细记录
            List<SplatoonBattleUserDetail> userDetailList = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                    .in(SplatoonBattleUserDetail::getBattleId, recordList.stream().map(splatoonBattleRecord -> splatoonBattleRecord.getId().toString()).collect(Collectors.toList())));
            imageFile = splatoonHtmlRenderer.renderBattleList(recordList, userDetailList);
        }
        //打印耗时日志
        log.info(type.getKeyword() + "图片绘制耗时：" + startTime.until(LocalDateTime.now(), ChronoUnit.SECONDS) + "秒");

        //发送消息
        bbReplies.send(bbReceiveMessage, Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildLocalImageMessageContent(imageFile)));
    }


    /**
     * 对战详情:按序号(DB自增id)展示单场全队详情
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?对战详情"}, name = "对战详情")
    public void getBattleDetail(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        Matcher matcher = Pattern.compile("^/?对战详情\\s*(\\d+)").matcher(bbReceiveMessage.getMessage());
        if (!matcher.find()) {
            bbReplies.atText(bbReceiveMessage, "格式：对战详情 <序号>，如：对战详情 128");
            return;
        }
        Long id = Long.parseLong(matcher.group(1));
        List<String> accountIds = splatoonTokenManager.getDataUsers(bbReceiveMessage.getUserId()).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());
        SplatoonBattleRecord record = battleRecordService.getOne(new LambdaQueryWrapper<SplatoonBattleRecord>()
                .eq(SplatoonBattleRecord::getId, id).in(SplatoonBattleRecord::getUserId, accountIds));
        if (record == null) {
            bbReplies.atText(bbReceiveMessage, "没找到这条对战记录(序号 " + id + ")");
            return;
        }
        List<SplatoonBattleUserDetail> details = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                .eq(SplatoonBattleUserDetail::getBattleId, String.valueOf(id)));
        File imageFile = splatoonHtmlRenderer.renderBattleDetail(record, details);
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildLocalImageMessageContent(imageFile)));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    /**
     * 打工详情:按序号(DB自增id)展示单场全队/全wave详情
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?打工详情"}, name = "打工详情")
    public void getCoopDetail(BbReceiveMessage bbReceiveMessage) {
        if (!requireBound(bbReceiveMessage)) { return; }
        Matcher matcher = Pattern.compile("^/?打工详情\\s*(\\d+)").matcher(bbReceiveMessage.getMessage());
        if (!matcher.find()) {
            bbReplies.atText(bbReceiveMessage, "格式：打工详情 <序号>，如：打工详情 56");
            return;
        }
        Long id = Long.parseLong(matcher.group(1));
        List<String> accountIds = splatoonTokenManager.getDataUsers(bbReceiveMessage.getUserId()).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());
        SplatoonCoopRecord record = coopRecordService.getOne(new LambdaQueryWrapper<SplatoonCoopRecord>()
                .eq(SplatoonCoopRecord::getId, id).in(SplatoonCoopRecord::getUserId, accountIds));
        if (record == null) {
            bbReplies.atText(bbReceiveMessage, "没找到这条打工记录(序号 " + id + ")");
            return;
        }
        String coopId = String.valueOf(id);
        List<SplatoonCoopUserDetail> userDetails = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                .eq(SplatoonCoopUserDetail::getCoopId, coopId));
        List<com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail> waves = coopWaveDetailService.list(new LambdaQueryWrapper<com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail>()
                .eq(com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail::getCoopId, coopId)
                .orderByAsc(com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail::getWaveNumber));
        List<com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail> enemies = coopEnemyDetailService.list(new LambdaQueryWrapper<com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail>()
                .eq(com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail::getCoopId, coopId));
        File imageFile = splatoonHtmlRenderer.renderCoopDetail(record, userDetails, waves, enemies);
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildLocalImageMessageContent(imageFile)));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    private void sendSplatoonImage(BbReceiveMessage bbReceiveMessage, File imageFile) {
        bbReplies.send(bbReceiveMessage, Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildLocalImageMessageContent(imageFile)));
    }

    private String latestEventPeriodId(JSONObject pagination) {
        JSONObject data = pagination == null ? null : pagination.getJSONObject("data");
        JSONObject seasons = data == null ? null : data.getJSONObject("leagueMatchRankingSeasons");
        JSONArray seasonEdges = seasons == null ? null : seasons.getJSONArray("edges");
        if (seasonEdges == null || seasonEdges.isEmpty()) {
            return null;
        }
        JSONObject season = seasonEdges.getJSONObject(0).getJSONObject("node");
        JSONObject groups = season == null ? null : season.getJSONObject("leagueMatchRankingTimePeriodGroups");
        JSONArray groupEdges = groups == null ? null : groups.getJSONArray("edges");
        if (groupEdges == null || groupEdges.isEmpty()) {
            return null;
        }
        for (int i = 0; i < groupEdges.size(); i++) {
            JSONObject group = groupEdges.getJSONObject(i).getJSONObject("node");
            JSONArray timePeriods = group == null ? null : group.getJSONArray("timePeriods");
            if (timePeriods != null && !timePeriods.isEmpty()) {
                return timePeriods.getJSONObject(0).getString("id");
            }
        }
        return null;
    }

    /** 仅绑定用户可用喷喷战绩功能;未绑定则回提示并返回 false。 */
    private boolean requireBound(BbReceiveMessage bbReceiveMessage) {
        if (splatoonTokenManager.isBound(bbReceiveMessage.getUserId())) {
            return true;
        }
        bbReplies.atText(bbReceiveMessage, "你还没绑定喷喷账号，无法使用喷喷战绩功能。请联系管理员用「绑定喷喷账号」给你绑定后再用。");
        return false;
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
     * 获取可用 token。原本散布在这里的 4 + 4 次 DB 查询和字符串匹配 401 的逻辑
     * 已经迁移到 {@link com.bb.bot.common.util.nso.SplatoonTokenManager}。
     */
    public TokenInfo checkAndGetSplatoon3UserToken(String userId) {
        com.bb.bot.common.util.nso.SplatoonTokenManager.SplatoonToken token =
                splatoonTokenManager.getValid(userId);
        return new TokenInfo(token.webServiceToken(), token.bulletToken(), token.userInfo());
    }

}
