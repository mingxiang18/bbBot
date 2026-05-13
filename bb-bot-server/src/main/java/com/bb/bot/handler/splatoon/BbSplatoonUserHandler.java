package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
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
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private com.bb.bot.common.util.nso.SplatoonTokenManager splatoonTokenManager;

    @Autowired
    private com.bb.bot.handler.splatoon.render.CoopPointRenderer coopPointRenderer;

    @Autowired
    private com.bb.bot.handler.splatoon.render.SplatoonRecordRenderer splatoonRecordRenderer;

    /**
     * 自动上传喷喷记录
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"自动上传喷喷记录", "/自动上传喷喷记录", "关闭自动上传喷喷记录", "/关闭自动上传喷喷记录"}, name = "自动上传喷喷记录")
    public void autoUploadRecordsConfig(BbReceiveMessage bbReceiveMessage) {
        //获取用户配置
        UserConfigValue userConfigValue = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getUserId, bbReceiveMessage.getUserId())
                .eq(UserConfigValue::getType, "NSO")
                .eq(UserConfigValue::getKeyName, "autoUploadRecords"));

        //判断是开启还是关闭
        String openFlag = "0";
        if (!bbReceiveMessage.getMessage().contains("关闭")) {
            openFlag = "1";
        }

        //保存配置到数据库
        if (userConfigValue == null) {
            userConfigValue = new UserConfigValue();
            userConfigValue.setUserId(bbReceiveMessage.getUserId());
            userConfigValue.setType("NSO");
            userConfigValue.setKeyName("autoUploadRecords");
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
     * 获取喷喷好友列表
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^喷喷好友", "^/喷喷好友"}, name = "获取喷喷好友列表")
    public void getSplatoon3FriendList(BbReceiveMessage bbReceiveMessage) {
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
     * 上传打工记录
     */
    public void syncCoopRecords(String userId) {
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(userId);
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

        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //查询数据库记录
        List<SplatoonCoopRecord> recordList = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                .eq(SplatoonCoopRecord::getUserId, userAccountId)
                .orderByDesc(SplatoonCoopRecord::getPlayedTime)
                .last("limit " + (recordStart - 1) + "," + (recordEnd - recordStart + 1)));
        //查询数据库用户详细记录
        List<SplatoonCoopUserDetail> userDetailList = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                .in(SplatoonCoopUserDetail::getCoopId, recordList.stream().map(splatoonCoopRecord -> splatoonCoopRecord.getId().toString()).collect(Collectors.toList())));


        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();
        //绘制图片
        File imageFile = splatoonRecordRenderer.writeFullCoopRecord(recordList, userDetailList);
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
     * 上传对战记录
     */
    public void syncBattleRecords(String userId) {
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(userId);
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

        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //查询数据库记录
        List<SplatoonBattleRecord> recordList = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                .eq(SplatoonBattleRecord::getUserId, userAccountId)
                .orderByDesc(SplatoonBattleRecord::getPlayedTime)
                .last("limit " + (recordStart - 1) + "," + (recordEnd - recordStart + 1)));
        //查询数据库用户详细记录
        List<SplatoonBattleUserDetail> userDetailList = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                .in(SplatoonBattleUserDetail::getBattleId, recordList.stream().map(splatoonBattleRecord -> splatoonBattleRecord.getId().toString()).collect(Collectors.toList())));

        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();
        //绘制图片
        File imageFile = splatoonRecordRenderer.writeFullBattleRecord(recordList, userDetailList);
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
