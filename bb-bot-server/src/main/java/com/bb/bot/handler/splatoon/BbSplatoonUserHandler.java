package com.bb.bot.handler.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.common.util.Splatoon3ApiCaller;
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
import com.bb.bot.handler.nso.BbNsoHandler;
import com.bb.bot.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 喷喷获取已登录的用户事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB)
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
    private BbNsoHandler bbNsoHandler;

    public static Map<String, String> pointDiffMap = new HashMap<String, String>() {{
        put("UP", "↑");
        put("DOWN", "↓");
        put("KEEP", "→");
    }};

    public static Map<String, String> ruleMap = new HashMap<String, String>() {{
        put("REGULAR", "普通打工");
        put("TEAM_CONTEST", "团队工");
        put("BIG_RUN", "大型跑");
    }};

    public static Map<String, String> battleRuleMap = new HashMap<String, String>() {{
        put("VnNSdWxlLTI=", "nso_splatoon/battle/rule/ta.png");
        put("VnNSdWxlLTE=", "nso_splatoon/battle/rule/quyu.png");
        put("VnNSdWxlLTM=", "nso_splatoon/battle/rule/yuhu.png");
        put("VnNSdWxlLTQ=", "nso_splatoon/battle/rule/geli.png");
    }};

    public static Map<String, ModeStyle> modeStyleMap = new HashMap<String, ModeStyle>() {{
        put("VnNNb2RlLTE=", new ModeStyle("VnNNb2RlLTE=", "占地比赛", new Color(95, 255, 26), "nso_splatoon/battle/mode/regular.png"));
        put("VnNNb2RlLTUx", new ModeStyle("VnNNb2RlLTUx", "蛮颓比赛(开放)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        put("VnNNb2RlLTI=", new ModeStyle("VnNNb2RlLTI=", "蛮颓比赛(挑战)", new Color(255, 60, 26), "nso_splatoon/battle/mode/rank.png"));
        put("VnNNb2RlLTQ=", new ModeStyle("VnNNb2RlLTQ=", "活动比赛", new Color(255, 0, 98), "nso_splatoon/battle/mode/event.png"));
        put("VnNNb2RlLTU=", new ModeStyle("VnNNb2RlLTU=", "私人比赛", new Color(149, 0, 255), "nso_splatoon/battle/mode/private.png"));
        put("VnNNb2RlLTM=", new ModeStyle("VnNNb2RlLTM=", "X比赛", new Color(0, 131, 98), "nso_splatoon/battle/mode/x.png"));
        put("VnNNb2RlLTY=", new ModeStyle("VnNNb2RlLTY=", "祭典比赛", new Color(34, 220, 255, 255), "nso_splatoon/battle/mode/fest.png"));
        put("VnNNb2RlLTg=", new ModeStyle("VnNNb2RlLTg=", "三色夺宝比赛", new Color(34, 255, 248, 255), "nso_splatoon/battle/mode/fest.png"));
    }};

    public static Map<String, TeamStyle> teamStyleMap = new HashMap<String, TeamStyle>() {{
        put("team1", new TeamStyle(new Color(89, 181, 170), "nso_splatoon/battle/icon/kill.png", "nso_splatoon/battle/icon/death.png"));
        put("team2", new TeamStyle(new Color(180, 65, 106), "nso_splatoon/battle/icon/kill2.png", "nso_splatoon/battle/icon/death2.png"));
    }};

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
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //调用接口获取数据
        JSONObject userCoopData = splatoon3ApiCaller.getCoops(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good2.jpg"));
        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 360, 460);
        //从临时图片创建默认g2d对象
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        JSONObject coopResult = userCoopData.getJSONObject("data").getJSONObject("coopResult");
        JSONObject pointCard = coopResult.getJSONObject("pointCard");
        JSONObject scaleData = coopResult.getJSONObject("scale");

        //绘制标题
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 28, new Color(255, 48, 20),
                "熊先生点数卡",
                30, 40,
                200, 30,
                0);

        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 115, 0), 24, 60, 312, 380, 0.5f);


        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 218, 0), 28, 70, 300, 28, 0.3f);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "累计点数: " + pointCard.getInteger("totalPoint"),
                30, 90,
                200, 30,
                0);

        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 28, 102, 340, 160, 0.1f);
        Color wordColor = new Color(223, 223, 223, 255);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "打工次数: " + pointCard.getInteger("playCount"),
                30, 120,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已收集的金鲑鱼卵: " + pointCard.getInteger("goldenDeliverCount"),
                30, 150,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已收集的鲑鱼卵: " + pointCard.getInteger("deliverCount"),
                30, 180,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已击倒的头目鲑鱼: " + pointCard.getInteger("defeatBossCount"),
                30, 210,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "救援次数: " + pointCard.getInteger("rescueCount"),
                30, 240,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 26, Color.GREEN,
                "鳞片",
                30, 290,
                400, 30,
                0);

        //铜鳞片绘制
        File bronzeScale = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/bronze_scale.png"));
        ImageUtils.mergeImageToOtherImage(g2d, bronzeScale, 50, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("bronze"),
                60, 390,
                200, 30,
                0);

        //银鳞片绘制
        File sliverScale = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/sliver_scale.png"));
        ImageUtils.mergeImageToOtherImage(g2d, sliverScale, 146, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("silver"),
                156, 390,
                200, 30,
                0);

        //金鳞片绘制
        File goldScale = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/gold_scale.png"));
        ImageUtils.mergeImageToOtherImage(g2d, goldScale, 240, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("gold"),
                250, 390,
                200, 30,
                0);

        //将绘制完成的临时图片写入文件
        ImageUtils.writeG2dToFile(g2d, image, imageFile);

        //发送消息
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
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^打工记录", "^/打工记录"}, name = "打工记录")
    public void getCoopRecords(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("打工记录(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        Integer pageNum = null;
        Integer pageStart = null;
        // 如果找到匹配项
        if (matcher.find()) {
            pageNum = Integer.valueOf(matcher.group(1));
        }
        if (pageNum != null) {
            pageStart = (pageNum-1) * 5;
        }

        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));
        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();

        //从临时图片创建默认g2d对象
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        //查询数据库记录
        List<SplatoonCoopRecord> recordList = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                .eq(SplatoonCoopRecord::getUserId, userAccountId)
                .orderByDesc(SplatoonCoopRecord::getPlayedTime)
                .last("limit " + (pageStart == null ? "" : pageStart + ",") + "5"));

        int startY = 20;
        for (SplatoonCoopRecord record : recordList) {
            //查询数据库用户详细记录
            List<SplatoonCoopUserDetail> userDetailList = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                    .eq(SplatoonCoopUserDetail::getCoopId, record.getId().toString()));

            //绘制当前打工记录
            writeOneCoopRecord(g2d, record, userDetailList, startY);

            startY += 140;
        }

        //将绘制完成的临时图片写入文件
        ImageUtils.writeG2dToFile(g2d, image, imageFile);
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
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^对战记录", "^/对战记录"}, name = "对战记录")
    public void getBattleRecords(BbReceiveMessage bbReceiveMessage) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("对战记录(\\d+)");
        Matcher matcher = pattern.matcher(bbReceiveMessage.getMessage());
        Integer pageNum = null;
        Integer pageStart = null;
        // 如果找到匹配项
        if (matcher.find()) {
            pageNum = Integer.valueOf(matcher.group(1));
        }
        if (pageNum != null) {
            pageStart = (pageNum-1) * 5;
        }

        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(bbReceiveMessage.getUserId());
        //获取用户账户信息的id
        String userAccountId = tokenInfo.getUserInfo().getString("id");

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));
        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);
        //从临时图片创建默认g2d对象
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        //查询数据库记录
        List<SplatoonBattleRecord> recordList = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                .eq(SplatoonBattleRecord::getUserId, userAccountId)
                .orderByDesc(SplatoonBattleRecord::getPlayedTime)
                .last("limit " + (pageStart == null ? "" : pageStart + ",") + "5"));

        //记录图片绘制时间
        LocalDateTime startTime = LocalDateTime.now();

        int startY = 10;
        for (SplatoonBattleRecord record : recordList) {
            //查询数据库用户详细记录
            List<SplatoonBattleUserDetail> userDetailList = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                    .eq(SplatoonBattleUserDetail::getBattleId, record.getId().toString()));

            //绘制当前对战记录
            writeOneBattleRecord(g2d, record, userDetailList, startY);

            startY += 140;
        }

        //将绘制完成的临时图片写入文件
        ImageUtils.writeG2dToFile(g2d, image, imageFile);
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
     * 绘制一条打工记录
     */
    private void writeOneCoopRecord(Graphics2D g2d, SplatoonCoopRecord record, List<SplatoonCoopUserDetail> userDetailList, int startY) {
        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 115, 0), 15, startY, 690, 130, 0.3f);

        //绘制记录序号
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        //绘制打工时间
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 10, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        //绘制地图名称
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                record.getCoopStageName(),
                120, startY + 30,
                200, 30,
                0);

        //武器1绘制
        File weapon1 = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/weapon/" + record.getWeapon1() + ".png"));
        ImageUtils.mergeImageToOtherImage(g2d, weapon1, 380, startY + 5, 35, 35);
        //武器2绘制
        File weapon2 = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/weapon/" + record.getWeapon2() + ".png"));
        ImageUtils.mergeImageToOtherImage(g2d, weapon2, 420, startY + 5, 35, 35);
        //武器3绘制
        File weapon3 = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/weapon/" + record.getWeapon3() + ".png"));
        ImageUtils.mergeImageToOtherImage(g2d, weapon3, 460, startY + 5, 35, 35);
        //武器4绘制
        File weapon4 = new File(FileUtils.getAbsolutePath("nso_splatoon/coop/weapon/" + record.getWeapon4() + ".png"));
        ImageUtils.mergeImageToOtherImage(g2d, weapon4, 500, startY + 5, 35, 35);

        //绘制危险度底色
        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 560, startY + 3, 70, 38, 0.3f);

        //绘制危险度
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 11, Color.YELLOW,
                "危险度：" + record.getDangerRate() + "%",
                560, startY + 25,
                200, 30,
                0);

        //绘制团队蛋数底色
        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 3, 60, 38, 0.3f);

        //绘制金蛋数
        ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/gold.png")), 640, startY + 2, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamGlodenCount()),
                660, startY + 15,
                200, 30,
                0);

        //绘制红蛋数
        ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/red.png")), 640, startY + 22, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamRedCount()),
                660, startY + 35,
                200, 30,
                0);

        if (StringUtils.isNoneBlank(record.getAfterGradeId())) {
            String pointDiff = pointDiffMap.get(record.getGradePointDiff());
            //绘制分数
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 15, Color.YELLOW,
                    record.getAfterGradeName() + " " + record. getAfterGradePoint() + " " + pointDiff,
                    20, startY + 90,
                    200, 30,
                    0);
        }else {
            String ruleName = ruleMap.get(record.getRule());
            //如果没有分数，绘制模式名称
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 15, Color.YELLOW,
                    "模式：" + ruleName,
                    20, startY + 90,
                    200, 30,
                    0);
        }

        int userX = 120;
        Color color = Color.WHITE;
        for (SplatoonCoopUserDetail splatoonCoopUserDetail : userDetailList) {
            //绘制用户数据底色
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 135, 80 , 0.4f);

            //绘制名称
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "名称：" + splatoonCoopUserDetail.getPlayerName(),
                    userX, startY + 60,
                    200, 30,
                    0);
            //绘制击倒数
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "击倒数：" + splatoonCoopUserDetail.getDefeatEnemyCount(),
                    userX, startY + 80,
                    200, 30,
                    0);
            //绘制运蛋数
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/gold.png")),
                    userX, startY + 86, 18, 18);
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/red.png")),
                    userX + 60, startY + 86, 18, 18);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getDeliverGlodenCount() + "             " + splatoonCoopUserDetail.getDeliverRedCount(),
                    userX + 20, startY + 100,
                    200, 30,
                    0);
            //绘制救援数
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/rescue.png")),
                    userX, startY + 106, 36, 18);
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/coop/icon/rescued.png")),
                    userX + 60, startY + 106, 36, 18);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getRescueCount() + "             " + splatoonCoopUserDetail.getRescuedCount(),
                    userX + 40, startY + 120,
                    200, 30,
                    0);

            userX += 150;
        }
    }

    /**
     * 绘制一条对战记录
     */
    private void writeOneBattleRecord(Graphics2D g2d, SplatoonBattleRecord record, List<SplatoonBattleUserDetail> userDetailList, int startY) {
        ModeStyle modeStyle = BbSplatoonUserHandler.modeStyleMap.get(record.getVsModeId());
        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, modeStyle.getColor(), 15, startY, 700, 132, 0.3f);

        //绘制记录序号
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        //绘制对战时间
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 10, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        //绘制模式标志
        ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath(modeStyle.getModeImgPath())),
                120, startY + 8, 30, 30);

        //绘制规则标志,涂地模式没有标志，是不绘制的
        String ruleImgPath = BbSplatoonUserHandler.battleRuleMap.get(record.getVsRuleId());
        if (StringUtils.isNoneBlank(ruleImgPath)) {
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath(ruleImgPath)),
                    150, startY + 8, 30, 30);
        }

        //绘制地图
        ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/battle/stage/" + record.getVsStageId() + ".png")),
                190, startY + 8, 0.2, 1f);
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                record.getVsStageName(),
                360, startY + 30,
                200, 30,
                0);

        //绘制胜负
        Color judgeColor = Color.WHITE;
        Boolean isWin = false;
        if ("WIN".equals(record.getJudgement())) {
            judgeColor = Color.YELLOW;
            isWin = true;
        }else if ("LOSE".equals(record.getJudgement())) {
            judgeColor = Color.WHITE;
        }
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 20, judgeColor,
                record.getJudgement(),
                40, startY + 90,
                200, 30,
                0);

        //绘制分数变化
        if (record.getPointChange() != null) {
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 16, judgeColor,
                    (record.getPointChange() > 0 ? "+" + record.getPointChange() : record.getPointChange()) + "p",
                    560, startY + 25,
                    200, 30,
                    0);
        }

        //绘制xp数
        if (record.getPower() != null) {
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 7, 60, 30 , 0.3f);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 16, judgeColor,
                    "xp" + record.getPower(),
                    650, startY + 25,
                    200, 30,
                    0);
        }

        int userX = 120;

        //获取用户本人的队伍
        int meTeam = 0;
        for (SplatoonBattleUserDetail splatoonBattleUserDetail : userDetailList) {
            if (splatoonBattleUserDetail.getMeFlag() == 1) {
                meTeam = splatoonBattleUserDetail.getTeamOrder();
            }
        }
        //排序用户顺序, 规则：用户本人在第一位，用户本人队伍在前，其他队伍在后
        int finalMeTeam = meTeam;
        userDetailList = userDetailList.stream().sorted(new Comparator<SplatoonBattleUserDetail>() {
            @Override
            public int compare(SplatoonBattleUserDetail o1, SplatoonBattleUserDetail o2) {
                if (o1.getMeFlag() == 1) {
                    return -1;
                }else if (o2.getMeFlag() == 1) {
                    return 1;
                }else if (o1.getTeamOrder() == null){
                    return 1;
                }else if (o2.getTeamOrder() == null){
                    return -1;
                }else if (o1.getTeamOrder() == finalMeTeam){
                    return -1;
                }else if (o2.getTeamOrder() == finalMeTeam){
                    return 1;
                }else {
                    return o1.getTeamOrder().compareTo(o2.getTeamOrder());
                }
            }
        }).collect(Collectors.toList());

        TeamStyle teamStyle = teamStyleMap.get("team1");
        for (int i = 0; i < userDetailList.size(); i++) {
            SplatoonBattleUserDetail splatoonBattleUserDetail = userDetailList.get(i);
            //绘制用户数据底色
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 146, 40 , 0.6f);

            //绘制队伍标识
            ImageUtils.createRoundRectOnImage(g2d, teamStyle.getTeamColor(), userX - 2, startY + 48, 5, 5, 1f);

            //绘制武器图片
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/weapon/" + splatoonBattleUserDetail.getWeaponId() + ".png")),
                    userX - 2, startY + 50, 27, 27);
            //绘制用户名称
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 11, isWin ? Color.YELLOW : Color.GRAY,
                    splatoonBattleUserDetail.getPlayerName(),
                    userX + 26, startY + 60,
                    200, 30,
                    0);
            //绘制击倒数
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath(teamStyle.getTeamKillImg())),
                    userX + 21, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getKillCount() == null ? "null" : splatoonBattleUserDetail.getKillCount() + "(" + splatoonBattleUserDetail.getAssistCount() + ")",
                    userX + 50, startY + 76,
                    200, 30,
                    0);

            //绘制死亡数
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath(teamStyle.getTeamDeathImg())),
                    userX + 73, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getDeathCount() == null ? "null" : splatoonBattleUserDetail.getDeathCount().toString(),
                    userX + 100, startY + 76,
                    200, 30,
                    0);
            //绘制大招数
            ImageUtils.mergeImageToOtherImage(g2d, new File(FileUtils.getAbsolutePath("nso_splatoon/specialWeapon/" + splatoonBattleUserDetail.getWeaponSpecialId() + ".png")),
                    userX + 113, startY + 65, 15, 15);
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getSpecialCount() == null ? "null" : splatoonBattleUserDetail.getSpecialCount().toString(),
                    userX + 128, startY + 76,
                    200, 30,
                    0);

            //按照以下顺序切换xy坐标
            //1 3 5 7
            //2 4 6 8
            if (i % 2 == 0) {
                startY = startY + 44;
            }else if (i % 2 != 0) {
                userX = userX + 150;
                startY = startY - 44;
            }

            //切换小队颜色和图片
            if (i == 3) {
                teamStyle = BbSplatoonUserHandler.teamStyleMap.get("team2");
                isWin = !isWin;
            }
        }
    }

    /**
     * 喷喷模式绘制配置实体类
     */
    @Data
    @AllArgsConstructor
    public static class ModeStyle {
        private String modeId;
        private String modeName;
        private Color color;
        private String modeImgPath;
    }

    /**
     * 喷喷对战小队样式配置实体类
     */
    @Data
    @AllArgsConstructor
    public static class TeamStyle {
        private Color teamColor;
        private String teamKillImg;
        private String teamDeathImg;
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
    public TokenInfo checkAndGetSplatoon3UserToken(String userId) {
        //数据库获取用户的userInfo和webAccessToken
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
            return resetSplatoon3UserToken(userId);
        }

        try {
            splatoon3ApiCaller.getTest(bulletTokenConfig.getValueName(), webServiceTokenConfig.getValueName(), JSONObject.parseObject(userInfoConfig.getValueName()));
        }catch (Exception e) {
            //如果token过期，刷新token并获取结果
            if (e.getMessage().contains("401")) {
                return  resetSplatoon3UserToken(userId);
            }else {
                log.error("调用斯普拉遁3接口出错", e);
            }

        }

        return new TokenInfo(webServiceTokenConfig.getValueName(), bulletTokenConfig.getValueName(), JSONObject.parseObject(userInfoConfig.getValueName()));
    }

    /**
     * 刷新喷喷用户token
     */
    private TokenInfo resetSplatoon3UserToken(String userId) {
        //重新设置nso用户token
        bbNsoHandler.resetUserToken(userId);

        //数据库获取用户的userInfo和webAccessToken
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

        //数据库重新设置web服务token
        UserConfigValue webServiceTokenConfig = new UserConfigValue();
        webServiceTokenConfig.setUserId(userId);
        webServiceTokenConfig.setType("NSO");
        webServiceTokenConfig.setKeyName("webServiceToken");
        webServiceTokenConfig.setValueName(webServiceToken);
        userConfigValueService.resetUserConfigValue(webServiceTokenConfig);

        //获取bulletToken
        String bulletToken = splatoon3ApiCaller.getBulletToken(webServiceToken, userInfo);

        //数据库重新设置bulletToken
        UserConfigValue bulletTokenConfig = new UserConfigValue();
        bulletTokenConfig.setUserId(userId);
        bulletTokenConfig.setType("NSO");
        bulletTokenConfig.setKeyName("bulletToken");
        bulletTokenConfig.setValueName(bulletToken);
        userConfigValueService.resetUserConfigValue(bulletTokenConfig);

        return new TokenInfo(webServiceTokenConfig.getValueName(), bulletTokenConfig.getValueName(), userInfo);
    }

}
