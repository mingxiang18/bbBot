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
import java.util.List;
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
        if (!requireBound(bbReceiveMessage)) { return; }
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("^/?打工记录(\\d*)-?(\\d*)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        int recordStart = 1;
        int recordEnd = 5;
        // 如果找到匹配项
        if (matcher.matches()) {
            // 提取前后数字并处理默认值
            recordStart = matcher.group(1).isEmpty() ? recordStart : Integer.parseInt(matcher.group(1));
            recordEnd = matcher.group(2).isEmpty() ? recordEnd : Integer.parseInt(matcher.group(2));
        } else {
            //返回格式不匹配
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("格式不正确，参考格式：【打工记录】、【打工记录2-11】"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }
        if (recordEnd - recordStart + 1 > 20) {
            //查询记录太多时返回
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("查询记录超过20条了，太多啦"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //该用户绑定的全部账号(查战绩跨账号聚合)
        List<String> accountIds = splatoonTokenManager.getDataUsers(bbReceiveMessage.getUserId()).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());

        //查询数据库记录
        List<SplatoonCoopRecord> recordList = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                .in(SplatoonCoopRecord::getUserId, accountIds)
                .orderByDesc(SplatoonCoopRecord::getPlayedTime)
                .last("limit " + (recordStart - 1) + "," + (recordEnd - recordStart + 1)));
        //无记录时直接提示(否则下面的 in 空列表会拼出非法 SQL)
        if (recordList.isEmpty()) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("还没有打工记录，先发【上传打工记录】或等自动上传跑过一轮")));
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }
        //查询数据库用户详细记录
        List<SplatoonCoopUserDetail> userDetailList = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                .in(SplatoonCoopUserDetail::getCoopId, recordList.stream().map(splatoonCoopRecord -> splatoonCoopRecord.getId().toString()).collect(Collectors.toList())));


        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();
        //绘制图片(HTML 渲染器)
        File imageFile = splatoonHtmlRenderer.renderCoopList(recordList, userDetailList);
        //打印耗时日志
        log.info("打工记录图片绘制耗时：" + startTime.until(LocalDateTime.now(), ChronoUnit.SECONDS) + "秒");

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildLocalImageMessageContent(imageFile))
        );
        bbMessageApi.sendMessage(bbSendMessage);
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
        if (!requireBound(bbReceiveMessage)) { return; }
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("^/?对战记录(\\d*)-?(\\d*)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        int recordStart = 1;
        int recordEnd = 5;
        // 如果找到匹配项
        if (matcher.matches()) {
            // 提取前后数字并处理默认值
            recordStart = matcher.group(1).isEmpty() ? recordStart : Integer.parseInt(matcher.group(1));
            recordEnd = matcher.group(2).isEmpty() ? recordEnd : Integer.parseInt(matcher.group(2));
        } else {
            //返回格式不匹配
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("格式不正确，参考格式：【对战记录】、【对战记录2-11】"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }
        if (recordEnd - recordStart + 1 > 20) {
            //查询记录太多时返回
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("查询记录超过20条了，太多啦"))
            );
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        //该用户绑定的全部账号(查战绩跨账号聚合)
        List<String> accountIds = splatoonTokenManager.getDataUsers(bbReceiveMessage.getUserId()).stream()
                .map(SplatoonTokenManager::accountId).collect(Collectors.toList());

        //查询数据库记录
        List<SplatoonBattleRecord> recordList = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                .in(SplatoonBattleRecord::getUserId, accountIds)
                .orderByDesc(SplatoonBattleRecord::getPlayedTime)
                .last("limit " + (recordStart - 1) + "," + (recordEnd - recordStart + 1)));
        //无记录时直接提示(否则下面的 in 空列表会拼出非法 SQL)
        if (recordList.isEmpty()) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("还没有对战记录，先发【上传对战记录】或等自动上传跑过一轮")));
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }
        //查询数据库用户详细记录
        List<SplatoonBattleUserDetail> userDetailList = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                .in(SplatoonBattleUserDetail::getBattleId, recordList.stream().map(splatoonBattleRecord -> splatoonBattleRecord.getId().toString()).collect(Collectors.toList())));

        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();
        //绘制图片(HTML 渲染器)
        File imageFile = splatoonHtmlRenderer.renderBattleList(recordList, userDetailList);
        //打印耗时日志
        log.info("对战记录图片绘制耗时：" + startTime.until(LocalDateTime.now(), ChronoUnit.SECONDS) + "秒");

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildLocalImageMessageContent(imageFile))
        );
        bbMessageApi.sendMessage(bbSendMessage);
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
