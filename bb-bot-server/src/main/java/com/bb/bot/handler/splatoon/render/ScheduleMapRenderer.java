package com.bb.bot.handler.splatoon.render;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.common.util.ResourcesUtils;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * 斯普拉遁 3 对战 / 打工 / 祭典 / 活动地图渲染器。
 *
 * <p>本类是 {@code BbSplatoonHandler} 里 7 个 {@code @SneakyThrows private} 渲染方法的
 * 纯位置迁移：方法体逐字搬过来，常量映射 {@code ruleModeMap}、{@code bossImgMap} 也跟着搬。
 * 由于渲染本身是确定性的（同输入 → 同字节输出），重构后的图与重构前像素级一致。
 *
 * @author ren
 */
@Component
public class ScheduleMapRenderer {

    @Autowired
    private ResourcesUtils resourcesUtils;

    @Autowired
    private SplatoonImageFetcher imageFetcher;

    private final HashMap<String, String> ruleModeMap = new HashMap<String, String>() {{
        put("Turf War", "涂地");
        put("Clam Blitz", "蛤蜊");
        put("Tower Control", "占塔");
        put("Splat Zones", "区域");
        put("Rainmaker", "抢鱼");
    }};

    private final HashMap<String, String> bossImgMap = new HashMap<String, String>() {{
        put("Q29vcEVuZW15LTI0", "nso_splatoon/coop/boss/chenlong.png");
        put("Q29vcEVuZW15LTI1", "nso_splatoon/coop/boss/jue.png");
        put("Q29vcEVuZW15LTIz", "nso_splatoon/coop/boss/henggang.png");
        put("Q29vcEVuZW15LTMw", "nso_splatoon/coop/boss/toumulianhe.png");
    }};

    /** splatoon3对战地图绘制 */
    @SneakyThrows
    public File writeRegularMap(JSONObject scheduleData, int timeIndex) {
        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 587);

        if (timeIndex != -1) {
            BufferedImage image = writeOneRegularMap(imageFile, scheduleData, timeIndex);
            ImageIO.write(image, "png", imageFile);
        } else {
            int nodeSize = scheduleData.getJSONObject("regularSchedules").getJSONArray("nodes").size();
            List<BufferedImage> imageList = new ArrayList<>();
            for (int i = 0; i < nodeSize; i++) {
                BufferedImage bufferedImage = writeOneRegularMap(imageFile, scheduleData, i);
                if (bufferedImage != null) {
                    imageList.add(bufferedImage);
                }
            }
            BufferedImage mergedImage = ImageUtils.mergeImagesVertically(imageList);
            ImageIO.write(mergedImage, "png", imageFile);
        }

        return imageFile;
    }

    /** splatoon3单个时段的对战地图绘制 */
    @SneakyThrows
    public BufferedImage writeOneRegularMap(File backgroundImage, JSONObject scheduleData, int timeIndex) {
        BufferedImage image = ImageIO.read(backgroundImage);

        if (scheduleData.getJSONObject("regularSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("festMatchSettings") != null) {
            BufferedImage subImage = image.getSubimage(0, 0, 600, 293);
            Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(subImage);
            JSONArray festMatchSettings = scheduleData.getJSONObject("festSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("festMatchSettings");
            Optional<Object> openModeSchedule = festMatchSettings.stream()
                    .filter(festMatchSetting -> "REGULAR".equals(((JSONObject) festMatchSetting).getString("festMode"))).findFirst();
            Optional<Object> challengeModeSchedule = festMatchSettings.stream()
                    .filter(festMatchSetting -> "CHALLENGE".equals(((JSONObject) festMatchSetting).getString("festMode"))).findFirst();
            if (openModeSchedule.isEmpty() || challengeModeSchedule.isEmpty()) {
                return null;
            }
            regularMapWriteFromSchedules(g2d, (JSONObject) openModeSchedule.get(), 20, 15, "regular");
            regularMapWriteFromSchedules(g2d, (JSONObject) challengeModeSchedule.get(), 20, 145, "rank");

            JSONObject timeObject = scheduleData.getJSONObject("festSchedules").getJSONArray("nodes").getJSONObject(timeIndex);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 23, Color.WHITE,
                    "所处时段：" + DateUtils.convertUTCTimeToShowString(timeObject.getString("startTime"), timeObject.getString("endTime")),
                    230, 285,
                    500, 500,
                    0);

            return subImage;
        } else {
            Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);
            JSONObject scheduleObject = scheduleData.getJSONObject("regularSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONObject("regularMatchSetting");
            regularMapWriteFromSchedules(g2d, scheduleObject, 20, 15, "regular");

            scheduleObject = scheduleData.getJSONObject("bankaraSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("bankaraMatchSettings").getJSONObject(0);
            regularMapWriteFromSchedules(g2d, scheduleObject, 20, 145, "rank");

            scheduleObject = scheduleData.getJSONObject("bankaraSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("bankaraMatchSettings").getJSONObject(1);
            regularMapWriteFromSchedules(g2d, scheduleObject, 20, 275, "league1");

            scheduleObject = scheduleData.getJSONObject("xSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONObject("xMatchSetting");
            regularMapWriteFromSchedules(g2d, scheduleObject, 20, 405, "x");

            JSONObject timeObject = scheduleData.getJSONObject("regularSchedules").getJSONArray("nodes").getJSONObject(timeIndex);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 23, Color.WHITE,
                    "所处时段：" + DateUtils.convertUTCTimeToShowString(timeObject.getString("startTime"), timeObject.getString("endTime")),
                    230, 555,
                    500, 500,
                    0);

            return image;
        }
    }

    /** splatoon3打工地图绘制 */
    @SneakyThrows
    public File writeCoopMap(JSONObject scheduleData, int timeIndex) {
        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 754, 337);
        JSONArray scheduleArray = scheduleData.getJSONObject("coopGroupingSchedule").getJSONObject("regularSchedules").getJSONArray("nodes");

        if (timeIndex != -1) {
            BufferedImage image = writeOneCoopMap(imageFile, scheduleArray, timeIndex);
            if (timeIndex + 1 < scheduleArray.size()) {
                BufferedImage image2 = writeOneCoopMap(imageFile, scheduleArray, timeIndex + 1);
                image = ImageUtils.mergeImagesVertically(Arrays.asList(image, image2));
            }
            ImageIO.write(image, "png", imageFile);
        } else {
            int nodeSize = scheduleArray.size();
            List<BufferedImage> imageList = new ArrayList<>();
            for (int i = 0; i < nodeSize; i++) {
                imageList.add(writeOneCoopMap(imageFile, scheduleArray, i));
            }
            BufferedImage mergedImage = ImageUtils.mergeImagesVertically(imageList);
            ImageIO.write(mergedImage, "png", imageFile);
        }

        return imageFile;
    }

    /** splatoon3单个打工地图绘制 */
    @SneakyThrows
    public BufferedImage writeOneCoopMap(File backgroundImage, JSONArray scheduleArray, int timeIndex) {
        BufferedImage image = ImageIO.read(backgroundImage);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);
        coopMapWriteFromSchedules(g2d, scheduleArray.getJSONObject(timeIndex), 40, 50);
        return image;
    }

    /** 祭典海报渲染（之前是 BbSplatoonHandler.festivalHandle 里的内联渲染段）。 */
    @SneakyThrows
    public File writeFestivalPoster(JSONObject festivalObject, JSONObject transferObject) {
        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 450);
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        String id = festivalObject.getString("__splatoon3ink_id");

        String festivalName = transferObject.getJSONObject("festivals").getJSONObject(id).getString("title");
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 33, Color.WHITE,
                "祭典主题：" + festivalName,
                40, 75,
                600, 600,
                0);

        String imageUrl = festivalObject.getJSONObject("image").getString("url");
        ImageUtils.mergeImageToOtherImage(g2d, imageFetcher.getImageFile(imageUrl, "festival"), 40, 120, 0.3);

        JSONArray festivalTeams = transferObject.getJSONObject("festivals").getJSONObject(id).getJSONArray("teams");
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(0).getString("teamName"),
                70, 360,
                600, 600,
                0);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(1).getString("teamName"),
                250, 360,
                600, 600,
                0);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 35, Color.WHITE,
                festivalTeams.getJSONObject(2).getString("teamName"),
                430, 360,
                600, 600,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 20, Color.YELLOW,
                "祭典时间：" + DateUtils.convertUTCTimeToALLShowString(festivalObject.getString("startTime"), festivalObject.getString("endTime")),
                100, 410,
                600, 600,
                0);

        ImageUtils.writeG2dToFile(g2d, image, imageFile);
        return imageFile;
    }

    /** 活动列表海报渲染（BbSplatoonHandler.eventHandle 的内联段）。 */
    @SneakyThrows
    public File writeEventPoster(JSONObject scheduleData, JSONObject transferObject) {
        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 674);
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        eventMapWriteFromSchedules(g2d, scheduleData.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(0),
                transferObject, 0, 0);

        eventMapWriteFromSchedules(g2d, scheduleData.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(1),
                transferObject, 0, 335);

        ImageUtils.writeG2dToFile(g2d, image, imageFile);
        return imageFile;
    }

    /** 对战地图绘制 */
    @SneakyThrows
    private void regularMapWriteFromSchedules(Graphics2D g2d, JSONObject scheduleObject, int x, int y, String modeName) {
        String ruleMode = scheduleObject.getJSONObject("vsRule").getString("name");

        String imageUrl1 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(0).getJSONObject("image").getString("url");
        File imageFile1 = imageFetcher.getImageFile(imageUrl1, "stages");

        String imageUrl2 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(1).getJSONObject("image").getString("url");
        File imageFile2 = imageFetcher.getImageFile(imageUrl2, "stages");

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 42, Color.WHITE,
                ruleModeMap.get(ruleMode),
                x + 10, y + 40,
                30, 30,
                0);

        ImageUtils.mergeImageToOtherImage(g2d, imageFile1, x + 75, y, 0.6);
        ImageUtils.mergeImageToOtherImage(g2d, imageFile2, x + 325, y, 0.6);

        File iconImage = resourcesUtils.getStaticResource("splatoon/mode/" + modeName + ".png");
        ImageUtils.mergeImageToOtherImage(g2d, iconImage, x + 290, y + 25, 0.5);
    }

    /** 绘制打工地图 */
    @SneakyThrows
    private void coopMapWriteFromSchedules(Graphics2D g2d, JSONObject scheduleObject, int x, int y) {
        JSONObject bassInfo = scheduleObject.getJSONObject("setting").getJSONObject("boss");
        if (bassInfo != null) {
            String imgPath = bossImgMap.get(bassInfo.getString("id"));
            if (imgPath != null) {
                File bossImg = resourcesUtils.getStaticResource(imgPath);
                ImageUtils.mergeImageToOtherImage(g2d, bossImg, x, y - 25, 40, 40);
            }
        }

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 25, Color.WHITE,
                "所处时段：" + DateUtils.convertUTCTimeToALLShowString(scheduleObject.getString("startTime"), scheduleObject.getString("endTime")),
                x + 50, y,
                600, 600,
                0);

        String mapUrl = scheduleObject.getJSONObject("setting").getJSONObject("coopStage").getJSONObject("thumbnailImage").getString("url");
        File mapFile = imageFetcher.getImageFile(mapUrl, "coop");
        ImageUtils.mergeImageToOtherImage(g2d, mapFile, x, y + 40, 1);

        JSONArray weapons = scheduleObject.getJSONObject("setting").getJSONArray("weapons");
        writeFileToBackground(g2d, imageFetcher.getImageFile(weapons.getJSONObject(0).getJSONObject("image").getString("url"), "weapons"),
                x + 440, y + 40, 90);
        writeFileToBackground(g2d, imageFetcher.getImageFile(weapons.getJSONObject(1).getJSONObject("image").getString("url"), "weapons"),
                x + 560, y + 40, 90);
        writeFileToBackground(g2d, imageFetcher.getImageFile(weapons.getJSONObject(2).getJSONObject("image").getString("url"), "weapons"),
                x + 440, y + 150, 90);
        writeFileToBackground(g2d, imageFetcher.getImageFile(weapons.getJSONObject(3).getJSONObject("image").getString("url"), "weapons"),
                x + 560, y + 150, 90);
    }

    /** 活动地图绘制 */
    @SneakyThrows
    private void eventMapWriteFromSchedules(Graphics2D g2d, JSONObject eventObject, JSONObject transferObject, int x, int y) {
        JSONObject scheduleObject = eventObject.getJSONObject("leagueMatchSetting");
        String id = scheduleObject.getJSONObject("leagueMatchEvent").getString("id");

        File iconImage = resourcesUtils.getStaticResource("splatoon/mode/event.png");
        ImageUtils.mergeImageToOtherImage(g2d, iconImage, x + 15, y, 1.2);

        String eventName = transferObject.getJSONObject("events").getJSONObject(id).getString("name");
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 35, Color.WHITE,
                eventName,
                x + 95, y + 50,
                600, 600,
                0);

        String eventDesc = transferObject.getJSONObject("events").getJSONObject(id).getString("desc");
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 20, Color.WHITE,
                eventDesc,
                x + 95, y + 90,
                600, 600,
                0);

        String ruleMode = scheduleObject.getJSONObject("vsRule").getString("name");
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 42, Color.WHITE,
                ruleModeMap.get(ruleMode),
                x + 30, y + 160,
                30, 30,
                0);

        String imageUrl1 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(0).getJSONObject("image").getString("url");
        File imageFile1 = imageFetcher.getImageFile(imageUrl1, "stages");
        ImageUtils.mergeImageToOtherImage(g2d, imageFile1, x + 95, y + 110, 0.6);

        String imageUrl2 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(1).getJSONObject("image").getString("url");
        File imageFile2 = imageFetcher.getImageFile(imageUrl2, "stages");
        ImageUtils.mergeImageToOtherImage(g2d, imageFile2, x + 345, y + 110, 0.6);

        JSONArray timePeriods = eventObject.getJSONArray("timePeriods");
        int offsetY = 250;
        int offsetLength = 16;
        for (int i = 0; i < timePeriods.size(); i++) {
            JSONObject timePeriod = timePeriods.getJSONObject(i);
            ImageUtils.writeWordInImage(g2d,
                    resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 16, Color.YELLOW,
                    "活动时间" + (i + 1) + "：" + DateUtils.convertUTCTimeToShowString(timePeriod.getString("startTime"), timePeriod.getString("endTime")),
                    x + 195, y + offsetY,
                    600, 600,
                    0);
            offsetY = offsetY + offsetLength;
        }
    }

    /** 将附加图片按指定宽度等比缩放后绘制到底图上 */
    @SneakyThrows
    private void writeFileToBackground(Graphics2D g2d, File subFile, int x, int y, int width) {
        BufferedImage subImage = ImageIO.read(subFile);
        double ratio = ((double) width) / subImage.getWidth();
        ImageUtils.mergeImageToOtherImage(g2d, subFile, x, y, ratio);
    }
}
