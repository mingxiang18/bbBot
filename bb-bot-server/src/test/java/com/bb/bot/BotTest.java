package com.bb.bot;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.*;
import com.bb.bot.common.util.imageUpload.ImageUploadApi;
import com.bb.bot.database.japaneseLearn.entity.JapaneseFifty;
import com.bb.bot.database.japaneseLearn.mapper.JapaneseFiftyMapper;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.userConfigInfo.mapper.UserConfigValueMapper;
import com.bb.bot.entity.qq.QqMessage;
import com.bb.bot.handler.oneBot.aiChat.AiChatHandler;
import com.bb.bot.handler.qq.splatoon.QqSplatoonHandler;
import com.bb.bot.handler.qq.splatoon.QqSplatoonUserHandler;
import com.bb.bot.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest
public class BotTest {

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private RestClient restClient;

    @Autowired
    private AiChatHandler aiChatHandler;

    @Autowired
    private JapaneseFiftyMapper japaneseFiftyMapper;

    @Autowired
    private NsoApiCaller nsoApiCaller;

    @Autowired
    private Splatoon3ApiCaller splatoon3ApiCaller;

    @Autowired
    private UserConfigValueMapper userConfigValueMapper;

    @Autowired
    private QqSplatoonUserHandler qqSplatoonUserHandler;

    @Autowired
    private ISplatoonCoopRecordsService coopRecordService;

    @Autowired
    private ISplatoonCoopUserDetailService coopUserDetailService;

    @Autowired
    private ImageUploadApi imageUploadApi;

    @Autowired
    private QqSplatoonHandler qqSplatoonHandler;

    @Autowired
    private ISplatoonBattleRecordService battleRecordService;

    @Autowired
    private ISplatoonBattleUserDetailService battleUserDetailService;

    @Test
    public void testRestClient() {
        restUtils.get("https://www.baidu.com/sadasdas", String.class);
    }

    @Test
    public void testSplatoonActivity() {
        QqSplatoonUserHandler.TokenInfo tokenInfo = qqSplatoonUserHandler.checkAndGetSplatoon3UserToken("3445764973117539607");
        //调用接口获取数据
        JSONObject data = splatoon3ApiCaller.getXBattleHistories(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());

        //调用接口获取对战详情
        JSONObject battleDetail = splatoon3ApiCaller.getCoops(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo());
    }

    @Test
    public void syncBattle() {
        QqMessage qqMessage = new QqMessage();
        QqMessage.QqUser qqUser = new QqMessage.QqUser();
        qqUser.setId("3445764973117539607");
        qqMessage.setAuthor(qqUser);
        qqSplatoonUserHandler.syncBattleRecords(qqMessage);
    }

    @Test
    public void tetete() {
        byte[] randomByte1 = NsoApiCaller.getRandom(36);
        String authState = NsoApiCaller.safeUrlBase64Encode(randomByte1);
        byte[] randomByte2 = NsoApiCaller.getRandom(32);
        String authCodeVerifier = NsoApiCaller.safeUrlBase64Encode(randomByte2);
        String authCodeChallenge = NsoApiCaller.safeUrlBase64Encode(NsoApiCaller.hashEncode(authCodeVerifier));

        //打印相关随机编码
        log.info("auth_state: " + authState);
        log.info("auth_code_verifier: " + authCodeVerifier);
        log.info("auth_code_challenge: " + authCodeChallenge);

        //将随机编码设置到缓存
        LocalCacheUtils.setCacheObject("-" + "auth_state", authState, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject("-" + "auth_code_verifier", authCodeVerifier, 5, ChronoUnit.MINUTES);
        LocalCacheUtils.setCacheObject("-" + "auth_code_challenge", authCodeChallenge, 5, ChronoUnit.MINUTES);

        //获取登录url
        String userLoginInUrl = nsoApiCaller.getUserLoginInUrl(authState, authCodeChallenge);

        //System.in代表标准输入(即键盘输入)
        Scanner sc = new Scanner(System.in);
        String loginInAnswer = sc.nextLine();

        String sessionToken = nsoApiCaller.getSessionToken(loginInAnswer,
                LocalCacheUtils.getCacheObject("-" + "auth_code_verifier"));
        System.out.println(sessionToken);
    }

    @Test
    public void testNintendoLogin() {
        //获取token
        QqSplatoonUserHandler.TokenInfo tokenInfo = qqSplatoonUserHandler.checkAndGetSplatoon3UserToken("3445764973117539607");
        //调用接口获取数据
        JSONObject response = splatoon3ApiCaller.getCoopDetail(tokenInfo.getBulletToken(), tokenInfo.getWebServiceToken(), tokenInfo.getUserInfo(), "Q29vcEhpc3RvcnlEZXRhaWwtdS1hamR1a2ZudXM0aGF0Mjc1MnVtbToyMDIzMDkwM1QxMzUxMDdfZDlkNjFkOTktZWU1Yi00MWIwLWI0ZDYtNTBiMmVkNTYxMGY2");

        String imgUrl = response.getJSONObject("data").getJSONObject("coopHistoryDetail").getJSONArray("weapons").getJSONObject(0).getJSONObject("image").getString("url");
        try (
                InputStream fileInputStream = restUtils.getFileInputStream(imgUrl);
                // 定义一个文件名字进行接收获取文件
                FileOutputStream fileOut = new FileOutputStream(new File("D:\\80d9f1bb2e4504a17fbb9d2c72603358d9e4a941a6619c890429d459629b677a_0.png"));){

            byte[] buf = new byte[1024 * 8];
            while (true) {
                int read = 0;
                if (fileInputStream != null) {
                    read = fileInputStream.read(buf);
                }
                if (read == -1) {
                    break;
                }
                fileOut.write(buf, 0, read);
            }
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        //获取背景图片
        File backgroundImage = new File("D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/splatoon/background/bg_good.jpg");
        //生成临时图片文件
        File imageFile =  new File("D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/tmp/" + System.currentTimeMillis() + ".png");
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        //从临时图片创建默认g2d对象
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);



        //将绘制完成的临时图片写入文件
        ImageUtils.writeG2dToFile(g2d, image, imageFile);
    }

    @Test
    public void testNsoPic() {
        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));
        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        //查询数据库记录
        List<SplatoonCoopRecord> recordList = coopRecordService.list(new LambdaQueryWrapper<SplatoonCoopRecord>()
                .eq(SplatoonCoopRecord::getUserId, "80bc54cc3d385acc")
                .orderByDesc(SplatoonCoopRecord::getPlayedTime)
                .last("limit 5"));

        int startY = 10;
        for (SplatoonCoopRecord record : recordList) {
            //查询数据库用户详细记录
            List<SplatoonCoopUserDetail> userDetailList = coopUserDetailService.list(new LambdaQueryWrapper<SplatoonCoopUserDetail>()
                    .eq(SplatoonCoopUserDetail::getCoopId, record.getId().toString()));

            writeCoopRecords2(imageFile, record, userDetailList, startY);

            startY += 140;
        }
    }

    @Test
    public void testNsoPicBattle() throws Exception {
        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));
        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        //查询数据库记录
        List<SplatoonBattleRecord> recordList = battleRecordService.list(new LambdaQueryWrapper<SplatoonBattleRecord>()
                .eq(SplatoonBattleRecord::getUserId, "80bc54cc3d385acc")
                .orderByDesc(SplatoonBattleRecord::getPlayedTime)
                .last("limit 4,5"));

        int startY = 20;

        LocalDateTime startTime = LocalDateTime.now();
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(10);

        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        for (SplatoonBattleRecord record : recordList) {
            //查询数据库用户详细记录
            List<SplatoonBattleUserDetail> userDetailList = battleUserDetailService.list(new LambdaQueryWrapper<SplatoonBattleUserDetail>()
                    .eq(SplatoonBattleUserDetail::getBattleId, record.getId().toString()));
            writeBattleRecords(g2d, record, userDetailList, startY, fixedThreadPool);

            startY += 140;
        }

        //等待所有异步任务执行完毕
        fixedThreadPool.shutdown();
        while (true) {
            log.info("判断是否结束");
            if (fixedThreadPool.isTerminated()) {
                //所有的子线程都结束了
                break;
            }else {
                Thread.sleep(1000);
            }
        }

        //打印耗时日志
        log.info("对战记录图片绘制耗时：" + startTime.until(LocalDateTime.now(), ChronoUnit.SECONDS) + "秒");

        ImageUtils.writeG2dToFile(g2d, image, imageFile);
    }

    @Test
    public void uploadImg() {
        //获取背景图片
        File backgroundImage = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/splatoon/background/bg_good.jpg");
        String url = imageUploadApi.uploadImage(backgroundImage);
        System.out.println(url);
    }

    public static void main2(String[] args) {
        //获取背景图片
        File backgroundImage = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/splatoon/background/bg_good.jpg");
        //生成临时图片文件
        File imageFile =  new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/tmp/aa.png");
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 660);

        //查询数据库是否已存在记录
        SplatoonCoopRecord record = new SplatoonCoopRecord();
        record.setId(1011l);
        record.setCoopStageName("生筋子系統交流道遺址");
        record.setAfterGradeName("传说");
        record.setAfterGradePoint(40);
        record.setGradePointDiff("UP");
        record.setWeapon1(".52加侖");
        record.setWeapon2("14式竹筒槍‧甲");
        record.setWeapon3("噴射清潔槍");
        record.setWeapon4("迴旋潑桶");
        record.setTeamRedCount(4012);
        record.setTeamGlodenCount(75);
        record.setPlayedTime(LocalDateTime.now());

        List<SplatoonCoopUserDetail> userDetailList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            SplatoonCoopUserDetail splatoonCoopUserDetail = new SplatoonCoopUserDetail();
            splatoonCoopUserDetail.setMeFlag(true);
            splatoonCoopUserDetail.setPlayerName("asd");
            splatoonCoopUserDetail.setDeliverRedCount(1201);
            splatoonCoopUserDetail.setDeliverGlodenCount(22);
            splatoonCoopUserDetail.setRescueCount(2);
            splatoonCoopUserDetail.setRescuedCount(0);
            splatoonCoopUserDetail.setDefeatEnemyCount(5);
            userDetailList.add(splatoonCoopUserDetail);
        }

        writeCoopRecords2(imageFile, record, userDetailList, 20);
        writeCoopRecords2(imageFile, record, userDetailList, 160);
    }

    @SneakyThrows
    private static void writeBattleRecords(Graphics2D g2d, SplatoonBattleRecord record, List<SplatoonBattleUserDetail> userDetailList, int startY, ExecutorService fixedThreadPool) {
        QqSplatoonUserHandler.ModeStyle modeStyle = QqSplatoonUserHandler.modeStyleMap.get(record.getVsModeId());
        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, modeStyle.getColor(), 15, startY, 700, 132, 0.3f);

        //绘制记录序号
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        //绘制对战时间
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 10, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        //绘制模式标志
        ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/" + modeStyle.getModeImgPath()),
                120, startY + 8, 30, 30);

        //绘制规则标志,涂地模式没有标志，是不绘制的
        String ruleImgPath = QqSplatoonUserHandler.battleRuleMap.get(record.getVsRuleId());
        if (StringUtils.isNoneBlank(ruleImgPath)) {
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/" + ruleImgPath),
                    150, startY + 8, 30, 30);
        }

        //绘制地图
        ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/battle/stage/" + record.getVsStageId() + ".png"),
                190, startY + 8, 0.2, 1f);
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 22, Color.YELLOW,
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
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 16, judgeColor,
                    (record.getPointChange() > 0 ? "+" + record.getPointChange() : record.getPointChange()) + "p",
                    560, startY + 25,
                    200, 30,
                    0);
        }

        //绘制xp数
        if (record.getPower() != null) {
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 7, 60, 30 , 0.3f);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 16, judgeColor,
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

        QqSplatoonUserHandler.TeamStyle teamStyle = QqSplatoonUserHandler.teamStyleMap.get("team1");
        for (int i = 0; i < userDetailList.size(); i++) {
            SplatoonBattleUserDetail splatoonBattleUserDetail = userDetailList.get(i);
            //绘制用户数据底色
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 146, 40 , 0.6f);

            //绘制小队标识
            ImageUtils.createRoundRectOnImage(g2d, teamStyle.getTeamColor(), userX - 2, startY + 48, 5, 5, 1f);

            //绘制武器图片
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/weapon/" + splatoonBattleUserDetail.getWeaponId() + ".png"),
                    userX - 2, startY + 50, 27, 27);
            //绘制用户名称
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 11, isWin ? Color.YELLOW : Color.GRAY,
                    splatoonBattleUserDetail.getPlayerName(),
                    userX + 26, startY + 60,
                    200, 30,
                    0);
            //绘制击倒数
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/" + teamStyle.getTeamKillImg()),
                    userX + 21, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getKillCount() == null ? "null" : splatoonBattleUserDetail.getKillCount() + "(" + splatoonBattleUserDetail.getAssistCount() + ")",
                    userX + 50, startY + 76,
                    200, 30,
                    0);

            //绘制死亡数
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/" + teamStyle.getTeamDeathImg()),
                    userX + 73, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getDeathCount() == null ? "null" : splatoonBattleUserDetail.getDeathCount().toString(),
                    userX + 100, startY + 76,
                    200, 30,
                    0);
            //绘制大招数
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/specialWeapon/" + splatoonBattleUserDetail.getWeaponSpecialId() + ".png"),
                    userX + 113, startY + 65, 15, 15);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 11, Color.WHITE,
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
                teamStyle = QqSplatoonUserHandler.teamStyleMap.get("team2");
                isWin = !isWin;
            }
        }
    }

    @SneakyThrows
    private static void writeCoopRecords2(File imageFile, SplatoonCoopRecord record, List<SplatoonCoopUserDetail> userDetailList, int startY) {
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        //绘制半透明底色
        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 115, 0), 15, startY, 690, 130, 0.3f);

        //绘制记录序号
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        //绘制打工时间
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 10, Color.WHITE,//todo
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        //绘制地图名称
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 22, Color.YELLOW,
                record.getCoopStageName(),
                120, startY + 30,
                200, 30,
                0);

        //武器1绘制
        File weapon1 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon1() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon1, 380, startY + 5, 35, 35);
        //武器2绘制
        File weapon2 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon2() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon2, 420, startY + 5, 35, 35);
        //武器3绘制
        File weapon3 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon3() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon3, 460, startY + 5, 35, 35);
        //武器4绘制
        File weapon4 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon4() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon4, 500, startY + 5, 35, 35);

        //绘制危险度底色
        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 560, startY + 3, 70, 38, 0.3f);

        //绘制危险度
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 11, Color.YELLOW,
                "危险度：" + record.getDangerRate() + "%",
                560, startY + 25,
                200, 30,
                0);

        //绘制团队蛋数底色
        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 3, 60, 38, 0.3f);

        //绘制金蛋数
        ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/gold.png"),
                640, startY + 2, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamGlodenCount()),
                660, startY + 15,
                200, 30,
                0);

        //绘制红蛋数
        ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/red.png"),
                640, startY + 22, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamRedCount()),
                660, startY + 35,
                200, 30,
                0);

        Map<String, String> pointDiffMap = new HashMap<>();
        pointDiffMap.put("UP", "↑");
        pointDiffMap.put("DOWN", "↓");
        pointDiffMap.put("KEEP", "→");
        Map<String, String> ruleMap = new HashMap<String, String>() {{
            put("REGULAR", "普通打工");
            put("TEAM_CONTEST", "团队工");
            put("BIG_RUN", "大型跑");
        }};
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
        for (SplatoonCoopUserDetail splatoonCoopUserDetail : userDetailList) {
            //绘制用户数据底色
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 135, 80 , 0.4f);

            //绘制名称
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    "名称：" + splatoonCoopUserDetail.getPlayerName(),
                    userX, startY + 60,
                    200, 30,
                    0);
            //绘制击倒数
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    "击倒数：" + splatoonCoopUserDetail.getDefeatEnemyCount(),
                    userX, startY + 80,
                    200, 30,
                    0);
            //绘制运蛋数
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/gold.png"),
                    userX, startY + 86, 18, 18);
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/red.png"),
                    userX + 60, startY + 86, 18, 18);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getDeliverGlodenCount() + "            " + splatoonCoopUserDetail.getDeliverRedCount(),
                    userX + 20, startY + 100,
                    200, 30,
                    0);
            //绘制救援数
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/rescue.png"),
                    userX, startY + 106, 36, 18);
            ImageUtils.mergeImageToOtherImage(g2d, new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/icon/rescued.png"),
                    userX + 60, startY + 106, 36, 18);
            ImageUtils.writeWordInImage(g2d,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getRescueCount() + "            " + splatoonCoopUserDetail.getRescuedCount(),
                    userX + 40, startY + 120,
                    200, 30,
                    0);

            userX += 150;
        }

        ImageUtils.writeG2dToFile(g2d, image, imageFile);
    }

    private static void writeCoopRecords(File imageFile, SplatoonCoopRecord record, List<SplatoonCoopUserDetail> userDetailList, int startY) {
        //绘制分割线
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 22, Color.WHITE,
                "-------------------------------------------------------------------------",
                20, startY,
                2000, 30,
                0,
                imageFile.getAbsolutePath());

        //绘制记录序号
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        //绘制打工时间
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        //绘制地图名称
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 22, Color.YELLOW,
                record.getCoopStageName(),
                140, startY + 30,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        //武器1绘制
        File weapon1 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon1() + ".png");
        ImageUtils.mergeImageToOtherImage(imageFile, weapon1, 380, startY + 5, 0.12, imageFile);
        //武器2绘制
        File weapon2 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon2() + ".png");
        ImageUtils.mergeImageToOtherImage(imageFile, weapon2, 420, startY + 5, 0.12, imageFile);
        //武器3绘制
        File weapon3 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon3() + ".png");
        ImageUtils.mergeImageToOtherImage(imageFile, weapon3, 460, startY + 5, 0.12, imageFile);
        //武器4绘制
        File weapon4 = new File("/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/nso_splatoon/coop/weapon/" + record.getWeapon4() + ".png");
        ImageUtils.mergeImageToOtherImage(imageFile, weapon4, 500, startY + 5, 0.12, imageFile);

        //绘制运蛋数
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.YELLOW,
                "红蛋数：" + record.getTeamRedCount(),
                580, startY + 15,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        //绘制运蛋数
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 12, Color.YELLOW,
                "金蛋数：" + record.getTeamGlodenCount(),
                580, startY + 35,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        Map<String, String> pointDiffMap = new HashMap<>();
        pointDiffMap.put("UP", "↑");
        pointDiffMap.put("DOWN", "↓");
        pointDiffMap.put("KEEP", "→");
        String pointDiff = pointDiffMap.get(record.getGradePointDiff());
        //绘制分数
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 15, Color.YELLOW,
                record.getAfterGradeName() + " " + record. getAfterGradePoint() + " " + pointDiff,
                20, startY + 80,
                200, 30,
                0,
                imageFile.getAbsolutePath());

        int userX = 140;
        for (SplatoonCoopUserDetail splatoonCoopUserDetail : userDetailList) {
            //绘制名称
            ImageUtils.writeWordInImage(imageFile,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    "名称：" + splatoonCoopUserDetail.getPlayerName(),
                    userX, startY + 60,
                    200, 30,
                    0,
                    imageFile.getAbsolutePath());
            //绘制击倒数
            ImageUtils.writeWordInImage(imageFile,
                    FileUtils.getAbsolutePath("font/sakura.ttf"), Font.PLAIN, 13, Color.WHITE,
                    "击倒数：" + splatoonCoopUserDetail.getDefeatEnemyCount(),
                    userX, startY + 80,
                    200, 30,
                    0,
                    imageFile.getAbsolutePath());
            //绘制运蛋数
            ImageUtils.writeWordInImage(imageFile,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    "红蛋：" + splatoonCoopUserDetail.getDeliverRedCount() + "，金蛋：" + splatoonCoopUserDetail.getDeliverGlodenCount(),
                    userX, startY + 100,
                    200, 30,
                    0,
                    imageFile.getAbsolutePath());
            //绘制救援数
            ImageUtils.writeWordInImage(imageFile,
                    "/D:/develop/bot/bbBot/bb-bot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 13, Color.WHITE,
                    "救人：" + splatoonCoopUserDetail.getRescueCount() + "，死亡：" + splatoonCoopUserDetail.getRescuedCount(),
                    userX, startY + 120,
                    200, 30,
                    0,
                    imageFile.getAbsolutePath());

            userX += 150;
        }
    }

    @Test
    public void imageUpload() throws Exception {
//        System.out.println(imageUploadApi.uploadImage(new File("C:\\Users\\ren\\Desktop\\Dingtalk_20231019160725.jpg")));
        imageUploadApi.deleteAllImage();
    }

    @Test
    public void randomJapaneseFifty() throws Exception {
        boolean loop = true;

        while (loop) {
            Integer jpFiftyCount = japaneseFiftyMapper.selectCount(null).intValue();
            int randomInt = RandomUtil.randomInt(0, jpFiftyCount);

            //如果随机数命中常出现的词，则再随机一次，减少重复出现概率
            List<Integer> frequencyNum = Arrays.asList(1,2,6,7,8,16,19,20,25,40);
            if (frequencyNum.contains(randomInt-1) || randomInt > 45) {
                randomInt = RandomUtil.randomInt(0, jpFiftyCount);
            }
            //再再随机一次，减少重复出现概率
            if (frequencyNum.contains(randomInt-1) || randomInt > 45) {
                randomInt = RandomUtil.randomInt(0, jpFiftyCount);
            }

            JapaneseFifty japaneseFifty = japaneseFiftyMapper.selectOne(new LambdaQueryWrapper<JapaneseFifty>()
                    .last("limit 1 offset " + randomInt));

            String question = null;
            //随机抽取
            int num = RandomUtil.randomInt(0, 3);
            if (num == 0) {
                question = japaneseFifty.getHiragana();
            } else if (num == 1) {
                question = japaneseFifty.getKatakana();
            } else if (num == 2) {
                question = japaneseFifty.getPhonetic();
            }

            System.out.println(question);

            Scanner sc = new Scanner(System.in);//System.in代表标准输入(即键盘输入)
            String answer = sc.nextLine();

            System.out.println("平假名：" + japaneseFifty.getHiragana() + ", 片假名：" + japaneseFifty.getKatakana() + ", 音标：" + japaneseFifty.getPhonetic() + ", 提示词：" + japaneseFifty.getTips());

            if (answer.equals("exit")) {
                loop = false;
            }
        }
    }

    @Test
    public void aiChat() throws Exception {
        String reply = aiChatHandler.askChatGPT("你好", null);
        System.out.println(reply);
    }

    @Test
    public void saveImage() throws Exception {
        InputStream fileInputStream = restUtils.getFileInputStream("https://splatoon3.ink/assets/event.6d1af9f3.svg");

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        OutputStream output = new FileOutputStream(imageFile);
        output = new FileOutputStream(imageFile);
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buf)) > 0) {
            output.write(buf, 0, bytesRead);
        }
        fileInputStream.close();
        output.close();
    }

    @Test
    public void writeCoopMap() throws Exception {
        //发起网络请求获取json数据
        JSONObject dataObject = restUtils.get("https://splatoon3.ink/data/schedules.json", JSONObject.class).getJSONObject("data");

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        int timeIndex = 0;

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 754, 654);

        JSONArray scheduleArray = dataObject.getJSONObject("coopGroupingSchedule").getJSONObject("regularSchedules").getJSONArray("nodes");
        coopMapWriteFromSchedules(imageFile, scheduleArray.getJSONObject(timeIndex), 40, 50);
        coopMapWriteFromSchedules(imageFile, scheduleArray.getJSONObject(timeIndex+1), 40, 370);
    }

    @Test
    public void writeFestival() throws Exception {
        //发起网络请求获取json数据
        JSONObject dataObject = restUtils.get("https://splatoon3.ink/data/festivals.json", JSONObject.class).getJSONObject("JP").getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restUtils.get("https://splatoon3.ink/data/locale/zh-CN.json", JSONObject.class);

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分背景
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 450);

        //获取祭典节点
        JSONObject festivalObject = dataObject.getJSONObject("festRecords").getJSONArray("nodes").getJSONObject(0);
        String id = festivalObject.getString("__splatoon3ink_id");

        //绘制祭典标题
        String festivalName = transferObject.getJSONObject("festivals").getJSONObject(id).getString("title");
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 33, Color.WHITE,
                "祭典主题：" + festivalName,
                40, 75,
                600, 600,
                0,
                imageFile.getAbsolutePath());

        //绘制祭典图片
        String imageUrl = festivalObject.getJSONObject("image").getString("url");
        ImageUtils.mergeImageToOtherImage(imageFile, getImageFile(imageUrl, "festival"), 40, 120, 0.3, imageFile);

        //绘制祭典分组名称
        JSONArray festivalTeams = transferObject.getJSONObject("festivals").getJSONObject(id).getJSONArray("teams");
        //莎莎队
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(0).getString("teamName"),
                70, 360,
                600, 600,
                0,
                imageFile.getAbsolutePath());
        //曼曼队
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(1).getString("teamName"),
                250, 360,
                600, 600,
                0,
                imageFile.getAbsolutePath());
        //鬼蝠队
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(2).getString("teamName"),
                430, 360,
                600, 600,
                0,
                imageFile.getAbsolutePath());

        //绘制祭典时间
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                "祭典时间：" + DateUtils.convertUTCTimeToALLShowString(festivalObject.getString("startTime"), festivalObject.getString("endTime")),
                100, 410,
                600, 600,
                0,
                imageFile.getAbsolutePath());
    }

    @Test
    public void writeSplatoonEvent() throws Exception {
        //发起网络请求获取json数据
        JSONObject dataObject = restUtils.get("https://splatoon3.ink/data/schedules.json", JSONObject.class).getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restUtils.get("https://splatoon3.ink/data/locale/zh-CN.json", JSONObject.class);

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 674);

        //绘制活动1
        eventMapWriteFromSchedules(imageFile, dataObject.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(0),
                transferObject, 0, 0);

        //绘制活动2
        eventMapWriteFromSchedules(imageFile, dataObject.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(1),
                transferObject, 0, 335);
    }

    /**
     * 对战地图绘制
     */
    private void eventMapWriteFromSchedules(File imageFile, JSONObject eventObject, JSONObject transferObject, int x, int y) {
        HashMap<String, String> ruleModeMap = new HashMap<String, String>() {{
            put("Turf War", "涂地");
            put("Clam Blitz", "蛤蜊");
            put("Tower Control", "占塔");
            put("Splat Zones", "区域");
            put("Rainmaker", "抢鱼");
        }};

        //获取活动详情
        JSONObject scheduleObject = eventObject.getJSONObject("leagueMatchSetting");
        String id = scheduleObject.getJSONObject("leagueMatchEvent").getString("id");

        //获取活动图标
        File iconImage = new File(FileUtils.getAbsolutePath("splatoon/mode/event.png"));
        ImageUtils.mergeImageToOtherImage(imageFile, iconImage, x+15, y, 1.2, imageFile);

        //获取活动名称
        String eventName = transferObject.getJSONObject("events").getJSONObject(id).getString("name");
        //绘制活动名称
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 35, Color.WHITE,
                eventName,
                x+95, y+50,
                600, 600,
                0,
                imageFile.getAbsolutePath());

        //获取活动描述
        String eventDesc = transferObject.getJSONObject("events").getJSONObject(id).getString("desc");
        //绘制活动描述
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 20, Color.WHITE,
                eventDesc,
                x+95, y+90,
                600, 600,
                0,
                imageFile.getAbsolutePath());

        //对战模式获取
        String ruleMode = scheduleObject.getJSONObject("vsRule").getString("name");
        //绘制对战模式名称
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 42, Color.WHITE,
                ruleModeMap.get(ruleMode),
                x+30, y+160,
                30, 30,
                0,
                imageFile.getAbsolutePath());

        //地图1获取
        String imageUrl1 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(0).getJSONObject("image").getString("url");
        File imageFile1 = getImageFile(imageUrl1, "stages");
        //绘制地图1
        ImageUtils.mergeImageToOtherImage(imageFile, imageFile1, x+95, y+110, 0.6, imageFile);

        //地图2获取
        String imageUrl2 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(1).getJSONObject("image").getString("url");
        File imageFile2 = getImageFile(imageUrl2, "stages");
        //绘制地图2
        ImageUtils.mergeImageToOtherImage(imageFile, imageFile2, x+345, y+110, 0.6, imageFile);

        //活动时间获取
        JSONArray timePeriods = eventObject.getJSONArray("timePeriods");
        //绘制活动时间1
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                "活动时间1：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(0).getString("startTime"), timePeriods.getJSONObject(0).getString("endTime")),
                x+95, y+260,
                600, 600,
                0,
                imageFile.getAbsolutePath());
        //绘制活动时间2
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                "活动时间2：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(1).getString("startTime"), timePeriods.getJSONObject(1).getString("endTime")),
                x+95, y+280,
                600, 600,
                0,
                imageFile.getAbsolutePath());
        //绘制活动时间3
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                "活动时间3：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(2).getString("startTime"), timePeriods.getJSONObject(2).getString("endTime")),
                x+95, y+300,
                600, 600,
                0,
                imageFile.getAbsolutePath());
    }

    /**
     * 绘制打工地图
     */
    @SneakyThrows
    private void coopMapWriteFromSchedules(File imageFile, JSONObject scheduleObject, int x, int y) {
        //绘制打工时间
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 25, Color.WHITE,
                "所处时段：" + DateUtils.convertUTCTimeToALLShowString(scheduleObject.getString("startTime"), scheduleObject.getString("endTime")),
                x, y,
                600, 600,
                0,
                imageFile.getAbsolutePath());

        //绘制打工地图
        //地图获取
        String mapUrl = scheduleObject.getJSONObject("setting").getJSONObject("coopStage").getJSONObject("thumbnailImage").getString("url");
        File mapFile = getImageFile(mapUrl, "coop");
        //绘制地图
        ImageUtils.mergeImageToOtherImage(imageFile, mapFile, x, y+40, 1, imageFile);

        //绘制武器
        JSONArray weapons = scheduleObject.getJSONObject("setting").getJSONArray("weapons");
        writeFileToBackground(imageFile, getImageFile(weapons.getJSONObject(0).getJSONObject("image").getString("url"), "weapons"),
                x+440, y+40, 90);
        writeFileToBackground(imageFile, getImageFile(weapons.getJSONObject(1).getJSONObject("image").getString("url"), "weapons"),
                x+560, y+40, 90);
        writeFileToBackground(imageFile, getImageFile(weapons.getJSONObject(2).getJSONObject("image").getString("url"), "weapons"),
                x+440, y+150, 90);
        writeFileToBackground(imageFile, getImageFile(weapons.getJSONObject(3).getJSONObject("image").getString("url"), "weapons"),
                x+560, y+150, 90);
    }

    /**
     * 将附加图片按指定宽度等比缩放后绘制到底图上
     * @param width 宽度，会将图片等比缩放到该宽度
     */
    @SneakyThrows
    private void writeFileToBackground(File backgroundFile, File subFile, int x, int y, int width) {
        // 构造Image对象
        BufferedImage subImage = ImageIO.read(subFile);
        double ratio = ((double) width) / subImage.getWidth();

        File tmpImage = new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        ImageUtils.enlargementImageEqualProportion(subFile.getAbsolutePath(), tmpImage.getAbsolutePath(), ratio);

        BufferedImage image=ImageIO.read(tmpImage);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); // 设置绘制颜色为白色
        graphics.setStroke(new BasicStroke(2)); // 设置边框大小
        graphics.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1); // 绘制边框
        graphics.dispose(); // 释放绘图资源
        FileOutputStream fos=new FileOutputStream(tmpImage.getAbsolutePath());
        ImageIO.write(image, tmpImage.getName().substring(tmpImage.getName().lastIndexOf(".") + 1), fos);

        ImageUtils.mergeImageToOtherImage(backgroundFile, tmpImage, x, y, 1d, backgroundFile);
    }

    /**
     * 从url中获取图片
     * 如果图片已保存到本地，则从本地获取图片
     */
    private File getImageFile(String url, String type) {
        String fileName = null;
        if ("festival".equals(type)) {
            fileName = url.substring(url.lastIndexOf("/",url.lastIndexOf("/")-1)).replace("/", "");
        }else {
            fileName = url.substring(url.lastIndexOf("/"));
        }

        File file = new File(FileUtils.getAbsolutePath("splatoon/" + type + "/" + fileName));
        if (file.exists()) {
            return file;
        }else {
            byte[] data = FileUtils.getFile(url);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (OutputStream outStream = new FileOutputStream(file);){
                outStream.write(data);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

        return file;
    }
}
