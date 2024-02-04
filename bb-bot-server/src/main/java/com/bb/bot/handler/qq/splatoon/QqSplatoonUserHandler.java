package com.bb.bot.handler.qq.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.*;
import com.bb.bot.common.util.imageUpload.ImageUploadApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.handler.qq.nso.QqNsoHandler;
import com.bb.bot.util.FileUtils;
import com.bb.bot.util.RestClient;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 喷喷获取已登录的用户事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.QQ)
public class QqSplatoonUserHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private RestClient restClient;

    @Autowired
    private ImageUploadApi imageUploadApi;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private ISplatoonCoopRecordsService coopRecordService;

    @Autowired
    private ISplatoonCoopUserDetailService coopUserDetailService;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    @Autowired
    private QqNsoHandler qqNsoHandler;

    private Map<String, String> pointDiffMap = new HashMap<String, String>() {{
        put("UP", "↑");
        put("DOWN", "↓");
        put("KEEP", "→");
    }};

    private Map<String, String> ruleMap = new HashMap<String, String>() {{
        put("REGULAR", "普通打工");
        put("TEAM_CONTEST", "团队工");
        put("BIG_RUN", "大型跑");
    }};

    /**
     * 获取喷喷好友列表
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"喷喷好友", "/喷喷好友"}, name = "获取喷喷好友列表")
    public void getSplatoon3FriendList(QqMessage event) {
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(event.getAuthor().getId());
        //调用接口获取数据
        JSONObject friends = splatoon3ApiCaller.getFriends(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        StringBuilder returnMessage = new StringBuilder();
        //拼装好友登录状态
        JSONArray friendMessageList = friends.getJSONObject("data").getJSONObject("friends").getJSONArray("nodes");
        for (Object friendMessage : friendMessageList) {
            returnMessage.append("好友名：【" + ((JSONObject) friendMessage).getString("nickname") + "】" +
                    ",  在线状态：" + ((JSONObject) friendMessage).getString("onlineState") + "\n");
        }

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(returnMessage.toString());
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    /**
     * 上传打工记录
     */
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"上传打工记录", "/上传打工记录"}, name = "上传打工记录")
    public void syncCoopRecords(QqMessage event) {
        //获取token
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(event.getAuthor().getId());
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

        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent("上传打工记录完成");
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    /**
     * 打工记录
     */
    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^打工记录", "^/打工记录"}, name = "打工记录")
    public void getCoopRecords(QqMessage event) {
        // 定义正则表达式模式
        Pattern pattern = Pattern.compile("打工记录(\\d+)");
        Matcher matcher = pattern.matcher(event.getContent());
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
        TokenInfo tokenInfo = checkAndGetSplatoon3UserToken(event.getAuthor().getId());
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

        //发送消息
        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(ChannelMessage.buildAtMessage(event.getAuthor().getId()));
        channelMessage.setFile(imageFile);
        channelMessage.setImage(imageUploadApi.uploadImage(imageFile));
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }

    /**
     * 绘制一条打工记录
     */
    private void writeOneCoopRecord(Graphics2D g2d, SplatoonCoopRecord record, List<SplatoonCoopUserDetail> userDetailList, int startY) {
        //绘制分割线
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.WHITE,
                "-------------------------------------------------------------------------",
                20, startY,
                2000, 30,
                0);

        //绘制记录序号
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        //绘制打工时间
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        //绘制地图名称
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                record.getCoopStageName(),
                140, startY + 30,
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

        //绘制运蛋数
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.YELLOW,
                "红蛋数：" + record.getTeamRedCount(),
                580, startY + 15,
                200, 30,
                0);

        //绘制运蛋数
        ImageUtils.writeWordInImage(g2d,
                FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 12, Color.YELLOW,
                "金蛋数：" + record.getTeamGlodenCount(),
                580, startY + 35,
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

        int userX = 140;
        Color color = Color.YELLOW;
        for (SplatoonCoopUserDetail splatoonCoopUserDetail : userDetailList) {
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
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "金蛋：" + splatoonCoopUserDetail.getDeliverGlodenCount() + "，红蛋：" + splatoonCoopUserDetail.getDeliverRedCount(),
                    userX, startY + 100,
                    200, 30,
                    0);
            //绘制救援数
            ImageUtils.writeWordInImage(g2d,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "救人：" + splatoonCoopUserDetail.getRescueCount() + "，死亡：" + splatoonCoopUserDetail.getRescuedCount(),
                    userX, startY + 120,
                    200, 30,
                    0);

            userX += 150;
            color = Color.WHITE;
        }
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
        qqNsoHandler.resetUserToken(userId);

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
