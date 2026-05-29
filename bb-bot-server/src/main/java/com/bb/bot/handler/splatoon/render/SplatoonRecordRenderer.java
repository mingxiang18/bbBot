package com.bb.bot.handler.splatoon.render;

import cn.hutool.core.collection.ListUtil;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 喷喷 user handler 的图像渲染：打工记录 + 对战记录。
 *
 * <p>{@code writeFullCoopRecord}、{@code writeOneCoopRecord}、{@code writeFullBattleRecord}、
 * {@code writeOneBattleRecord} 四个原本嵌在 1145 行 handler 里的渲染方法，方法体逐字搬过来。
 * 关联的 {@link ModeStyle} / {@link TeamStyle} 配置 + 5 个静态 Map 也跟着搬。
 *
 * @author ren
 */
@Component
public class SplatoonRecordRenderer {

    @Autowired
    private ResourcesUtils resourcesUtils;

    /** 段位升降箭头、规则图标、模式样式三张表收敛至 {@link SplatoonStyleConfig}（两渲染器共用）。 */
    public static final Map<String, String> pointDiffMap = SplatoonStyleConfig.POINT_DIFF;

    /** 打工规则中文名表：文案与 HTML 渲染器不同，属本渲染器独有，不在共享范围内。 */
    public static final Map<String, String> ruleMap = new HashMap<String, String>() {{
        put("REGULAR", "普通打工");
        put("TEAM_CONTEST", "团队工");
        put("BIG_RUN", "大型跑");
    }};

    public static final Map<String, String> battleRuleMap = SplatoonStyleConfig.RULE_ICON;

    public static final Map<String, SplatoonStyleConfig.ModeStyle> modeStyleMap = SplatoonStyleConfig.MODE_STYLE;

    public static final Map<String, TeamStyle> teamStyleMap = new HashMap<String, TeamStyle>() {{
        put("team1", new TeamStyle(new Color(89, 181, 170), "nso_splatoon/battle/icon/kill.png", "nso_splatoon/battle/icon/death.png"));
        put("team2", new TeamStyle(new Color(180, 65, 106), "nso_splatoon/battle/icon/kill2.png", "nso_splatoon/battle/icon/death2.png"));
    }};

    /** 绘制完整打工记录图片 */
    @SneakyThrows
    public File writeFullCoopRecord(List<SplatoonCoopRecord> recordList, List<SplatoonCoopUserDetail> userDetailList) {
        List<List<SplatoonCoopRecord>> recordListPartition = ListUtil.partition(recordList, 5);

        Map<String, List<SplatoonCoopUserDetail>> userDetailListMap = userDetailList.stream()
                .collect(Collectors.groupingBy(SplatoonCoopUserDetail::getCoopId));

        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        List<BufferedImage> imageList = new ArrayList<>();

        for (List<SplatoonCoopRecord> fiveCoopRecordList : recordListPartition) {
            BufferedImage image = ImageIO.read(imageFile);
            Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

            int startY = 20;
            for (SplatoonCoopRecord record : fiveCoopRecordList) {
                List<SplatoonCoopUserDetail> coopUserDetailList = userDetailListMap.get(record.getId().toString());
                writeOneCoopRecord(g2d, record, coopUserDetailList, startY);
                startY += 140;
            }

            imageList.add(image);
        }

        BufferedImage mergedImage = ImageUtils.mergeImagesVertically(imageList);
        ImageIO.write(mergedImage, "png", imageFile);
        return imageFile;
    }

    /** 绘制一条打工记录 */
    @SneakyThrows
    public void writeOneCoopRecord(Graphics2D g2d, SplatoonCoopRecord record, List<SplatoonCoopUserDetail> userDetailList, int startY) {
        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 115, 0), 15, startY, 690, 130, 0.3f);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 10, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                record.getCoopStageName(),
                120, startY + 30,
                200, 30,
                0);

        File weapon1 = resourcesUtils.getStaticResource("nso_splatoon/coop/weapon/" + record.getWeapon1() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon1, 380, startY + 5, 35, 35);
        File weapon2 = resourcesUtils.getStaticResource("nso_splatoon/coop/weapon/" + record.getWeapon2() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon2, 420, startY + 5, 35, 35);
        File weapon3 = resourcesUtils.getStaticResource("nso_splatoon/coop/weapon/" + record.getWeapon3() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon3, 460, startY + 5, 35, 35);
        File weapon4 = resourcesUtils.getStaticResource("nso_splatoon/coop/weapon/" + record.getWeapon4() + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, weapon4, 500, startY + 5, 35, 35);

        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 560, startY + 3, 70, 38, 0.3f);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 11, Color.YELLOW,
                "危险度：" + record.getDangerRate() + "%",
                560, startY + 25,
                200, 30,
                0);

        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 3, 60, 38, 0.3f);

        ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/gold.png"), 640, startY + 2, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamGlodenCount()),
                660, startY + 15,
                200, 30,
                0);

        ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/red.png"), 640, startY + 22, 18, 18);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                String.valueOf(record.getTeamRedCount()),
                660, startY + 35,
                200, 30,
                0);

        if (StringUtils.isNoneBlank(record.getAfterGradeId())) {
            String pointDiff = pointDiffMap.get(record.getGradePointDiff());
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 15, Color.YELLOW,
                    record.getAfterGradeName() + " " + record.getAfterGradePoint() + " " + pointDiff,
                    20, startY + 90,
                    200, 30,
                    0);
        } else {
            String ruleName = ruleMap.get(record.getRule());
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 15, Color.YELLOW,
                    "模式：" + ruleName,
                    20, startY + 90,
                    200, 30,
                    0);
        }

        int userX = 120;
        Color color = Color.WHITE;
        for (SplatoonCoopUserDetail splatoonCoopUserDetail : userDetailList) {
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 135, 80, 0.4f);

            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "名称：" + splatoonCoopUserDetail.getPlayerName(),
                    userX, startY + 60,
                    200, 30,
                    0);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 13, color,
                    "击倒数：" + splatoonCoopUserDetail.getDefeatEnemyCount(),
                    userX, startY + 80,
                    200, 30,
                    0);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/gold.png"),
                    userX, startY + 86, 18, 18);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/red.png"),
                    userX + 60, startY + 86, 18, 18);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getDeliverGlodenCount() + "             " + splatoonCoopUserDetail.getDeliverRedCount(),
                    userX + 20, startY + 100,
                    200, 30,
                    0);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/rescue.png"),
                    userX, startY + 106, 36, 18);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/coop/icon/rescued.png"),
                    userX + 60, startY + 106, 36, 18);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 13, Color.WHITE,
                    splatoonCoopUserDetail.getRescueCount() + "             " + splatoonCoopUserDetail.getRescuedCount(),
                    userX + 40, startY + 120,
                    200, 30,
                    0);

            userX += 150;
        }
    }

    /** 绘制完整对战记录图片 */
    @SneakyThrows
    public File writeFullBattleRecord(List<SplatoonBattleRecord> recordList, List<SplatoonBattleUserDetail> userDetailList) {
        List<List<SplatoonBattleRecord>> recordListPartition = ListUtil.partition(recordList, 5);

        Map<String, List<SplatoonBattleUserDetail>> userDetailListMap = userDetailList.stream()
                .collect(Collectors.groupingBy(SplatoonBattleUserDetail::getBattleId));

        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 720, 720);

        List<BufferedImage> imageList = new ArrayList<>();

        for (List<SplatoonBattleRecord> fiveBattleRecordList : recordListPartition) {
            BufferedImage image = ImageIO.read(imageFile);
            Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

            int startY = 15;
            for (SplatoonBattleRecord record : fiveBattleRecordList) {
                List<SplatoonBattleUserDetail> battleUserDetailList = userDetailListMap.get(record.getId().toString());
                writeOneBattleRecord(g2d, record, battleUserDetailList, startY);
                startY += 140;
            }

            imageList.add(image);
        }

        BufferedImage mergedImage = ImageUtils.mergeImagesVertically(imageList);
        ImageIO.write(mergedImage, "png", imageFile);
        return imageFile;
    }

    /** 绘制一条对战记录 */
    @SneakyThrows
    public void writeOneBattleRecord(Graphics2D g2d, SplatoonBattleRecord record, List<SplatoonBattleUserDetail> userDetailList, int startY) {
        SplatoonStyleConfig.ModeStyle modeStyle = modeStyleMap.get(record.getVsModeId());
        ImageUtils.createRoundRectOnImage(g2d, modeStyle.getColor(), 15, startY, 700, 132, 0.3f);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 12, Color.WHITE,
                "序号：" + record.getId().toString(),
                20, startY + 20,
                200, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 10, Color.WHITE,
                record.getPlayedTime().format(DateUtils.normalTimePattern),
                20, startY + 40,
                200, 30,
                0);

        ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource(modeStyle.getModeImgPath()),
                120, startY + 8, 30, 30);

        String ruleImgPath = battleRuleMap.get(record.getVsRuleId());
        if (StringUtils.isNoneBlank(ruleImgPath)) {
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource(ruleImgPath),
                    150, startY + 8, 30, 30);
        }

        ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/battle/stage/" + record.getVsStageId() + ".png"),
                190, startY + 8, 0.2, 1f);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                record.getVsStageName(),
                360, startY + 30,
                200, 30,
                0);

        Color judgeColor = Color.WHITE;
        Boolean isWin = false;
        if ("WIN".equals(record.getJudgement())) {
            judgeColor = Color.YELLOW;
            isWin = true;
        } else if ("LOSE".equals(record.getJudgement())) {
            judgeColor = Color.WHITE;
        }
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 20, judgeColor,
                record.getJudgement(),
                40, startY + 90,
                200, 30,
                0);

        if (record.getPointChange() != null) {
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 16, judgeColor,
                    (record.getPointChange() > 0 ? "+" + record.getPointChange() : record.getPointChange()) + "p",
                    560, startY + 25,
                    200, 30,
                    0);
        }

        if (record.getPower() != null) {
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 640, startY + 7, 60, 30, 0.3f);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 16, judgeColor,
                    "xp" + record.getPower(),
                    650, startY + 25,
                    200, 30,
                    0);
        }

        int userX = 120;

        int meTeam = 0;
        for (SplatoonBattleUserDetail splatoonBattleUserDetail : userDetailList) {
            if (splatoonBattleUserDetail.getMeFlag() == 1) {
                meTeam = splatoonBattleUserDetail.getTeamOrder();
            }
        }
        int finalMeTeam = meTeam;
        userDetailList = userDetailList.stream().sorted(new Comparator<SplatoonBattleUserDetail>() {
            @Override
            public int compare(SplatoonBattleUserDetail o1, SplatoonBattleUserDetail o2) {
                if (o1.getMeFlag() == 1) {
                    return -1;
                } else if (o2.getMeFlag() == 1) {
                    return 1;
                } else if (o1.getTeamOrder() == null) {
                    return 1;
                } else if (o2.getTeamOrder() == null) {
                    return -1;
                } else if (o1.getTeamOrder() == finalMeTeam) {
                    return -1;
                } else if (o2.getTeamOrder() == finalMeTeam) {
                    return 1;
                } else {
                    return o1.getTeamOrder().compareTo(o2.getTeamOrder());
                }
            }
        }).collect(Collectors.toList());

        TeamStyle teamStyle = teamStyleMap.get("team1");
        for (int i = 0; i < userDetailList.size(); i++) {
            SplatoonBattleUserDetail splatoonBattleUserDetail = userDetailList.get(i);
            ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, userX - 5, startY + 45, 146, 40, 0.6f);

            ImageUtils.createRoundRectOnImage(g2d, teamStyle.getTeamColor(), userX - 2, startY + 48, 5, 5, 1f);

            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/weapon/" + splatoonBattleUserDetail.getWeaponId() + ".png"),
                    userX - 2, startY + 50, 27, 27);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 11, isWin ? Color.YELLOW : Color.GRAY,
                    splatoonBattleUserDetail.getPlayerName(),
                    userX + 26, startY + 60,
                    200, 30,
                    0);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource(teamStyle.getTeamKillImg()),
                    userX + 21, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getKillCount() == null ? "null" : splatoonBattleUserDetail.getKillCount() + "(" + splatoonBattleUserDetail.getAssistCount() + ")",
                    userX + 50, startY + 76,
                    200, 30,
                    0);

            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource(teamStyle.getTeamDeathImg()),
                    userX + 73, startY + 65, 30, 15);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getDeathCount() == null ? "null" : splatoonBattleUserDetail.getDeathCount().toString(),
                    userX + 100, startY + 76,
                    200, 30,
                    0);
            ImageUtils.mergeImageToOtherImage(g2d, resourcesUtils.getStaticResource("nso_splatoon/specialWeapon/" + splatoonBattleUserDetail.getWeaponSpecialId() + ".png"),
                    userX + 113, startY + 65, 15, 15);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 11, Color.WHITE,
                    splatoonBattleUserDetail.getSpecialCount() == null ? "null" : splatoonBattleUserDetail.getSpecialCount().toString(),
                    userX + 128, startY + 76,
                    200, 30,
                    0);

            if (i % 2 == 0) {
                startY = startY + 44;
            } else if (i % 2 != 0) {
                userX = userX + 150;
                startY = startY - 44;
            }

            if (i == 3) {
                teamStyle = teamStyleMap.get("team2");
                isWin = !isWin;
            }
        }
    }

    /** 喷喷对战小队样式配置实体类 */
    @Data
    @AllArgsConstructor
    public static class TeamStyle {
        private Color teamColor;
        private String teamKillImg;
        private String teamDeathImg;
    }
}
