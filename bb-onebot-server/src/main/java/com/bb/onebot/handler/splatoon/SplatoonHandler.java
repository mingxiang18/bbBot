package com.bb.onebot.handler.splatoon;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.ActionApi;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.MessageType;
import com.bb.onebot.constant.RuleType;
import com.bb.onebot.entity.MessageContent;
import com.bb.onebot.entity.ReceiveMessage;
import com.bb.onebot.event.ReceiveMessageEvent;
import com.bb.onebot.util.DateUtils;
import com.bb.onebot.util.FileUtils;
import com.bb.onebot.util.ImageUtils;
import com.bb.onebot.util.RestClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;

/**
 * 喷喷事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler
public class SplatoonHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private RestClient restClient;

    private HashMap<String, String> ruleModeMap = new HashMap<String, String>() {{
        put("Turf War", "涂地");
        put("Clam Blitz", "蛤蜊");
        put("Tower Control", "占塔");
        put("Splat Zones", "区域");
        put("Rainmaker", "抢鱼");
    }};

    /**
     * splatoon3对战地图获取
     * @author ren
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"图", "下图", "下下图", "下下下图"}, name = "对战地图获取")
    public void regularMapHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        //地图时间序号
        int timeIndex = 0;
        for (char c : message.getMessage().toCharArray()) {
            //统计下的次数，每次命中，时间序号加1
            if (c == '下') {
                timeIndex++;
            }
        }

        //发起网络请求获取json数据
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/schedules.json", httpHeaders, JSONObject.class).getJSONObject("data");

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 587);

        //涂地地图绘制
        JSONObject scheduleObject = dataObject.getJSONObject("regularSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONObject("regularMatchSetting");
        regularMapWriteFromSchedules(imageFile, scheduleObject, 20, 15, "regular");

        //单排地图绘制
        scheduleObject = dataObject.getJSONObject("bankaraSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("bankaraMatchSettings").getJSONObject(0);
        regularMapWriteFromSchedules(imageFile, scheduleObject, 20, 145, "rank");

        //组排地图绘制
        scheduleObject = dataObject.getJSONObject("bankaraSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONArray("bankaraMatchSettings").getJSONObject(1);
        regularMapWriteFromSchedules(imageFile, scheduleObject, 20, 275, "league1");

        //x比赛地图绘制
        scheduleObject = dataObject.getJSONObject("xSchedules").getJSONArray("nodes").getJSONObject(timeIndex).getJSONObject("xMatchSetting");
        regularMapWriteFromSchedules(imageFile, scheduleObject, 20, 405, "x");

        //绘制对战模式时间
        JSONObject timeObject = dataObject.getJSONObject("regularSchedules").getJSONArray("nodes").getJSONObject(timeIndex);
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 23, Color.WHITE,
                "所处时段：" + DateUtils.convertUTCTimeToShowString(timeObject.getString("startTime"), timeObject.getString("endTime")),
                230, 555,
                500, 500,
                0,
                imageFile.getAbsolutePath());

        MessageContent base64Content = MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64(imageFile));
        if (MessageType.GROUP.equals(message.getMessageType())) {
            //群聊要添加@用户
            MessageContent atMessageContent = MessageContent.buildAtMessageContent(userId);
            actionApi.sendGroupMessage(groupId, Arrays.asList(atMessageContent, base64Content));
        }else {
            actionApi.sendPrivateMessage(userId, base64Content);
        }
    }

    /**
     * splatoon3打工地图获取
     * @author ren
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"工", "下工", "下下工", "下下下工"}, name = "打工地图获取")
    public void coopMapHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        //地图时间序号
        int timeIndex = 0;
        for (char c : message.getMessage().toCharArray()) {
            //统计下的次数，每次命中，时间序号加1
            if (c == '下') {
                timeIndex++;
            }
        }

        //发起网络请求获取json数据
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/schedules.json", httpHeaders, JSONObject.class).getJSONObject("data");

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分底边
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 754, 674);

        //获取打工日程节点
        JSONArray scheduleArray = dataObject.getJSONObject("coopGroupingSchedule").getJSONObject("regularSchedules").getJSONArray("nodes");
        //绘制打工地图1
        coopMapWriteFromSchedules(imageFile, scheduleArray.getJSONObject(timeIndex), 40, 50);
        //绘制打工地图2
        coopMapWriteFromSchedules(imageFile, scheduleArray.getJSONObject(timeIndex+1), 40, 370);

        //发送消息
        MessageContent base64Content = MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64(imageFile));
        if (MessageType.GROUP.equals(message.getMessageType())) {
            //群聊要添加@用户
            MessageContent atMessageContent = MessageContent.buildAtMessageContent(userId);
            actionApi.sendGroupMessage(groupId, Arrays.asList(atMessageContent, base64Content));
        }else {
            actionApi.sendPrivateMessage(userId, base64Content);
        }
    }

    /**
     * splatoon3祭典日程
     * @author ren
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"祭典", "上祭典", "上上祭典"}, name = "祭典日程")
    public void festivalHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        //祭典时间序号
        int timeIndex = 0;
        for (char c : message.getMessage().toCharArray()) {
            //统计上的次数，每次命中，时间序号加1
            if (c == '上') {
                timeIndex++;
            }
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        //发起网络请求获取json数据
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/festivals.json", httpHeaders, JSONObject.class).getJSONObject("JP").getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restClient.get("https://splatoon3.ink/data/locale/zh-CN.json", httpHeaders, JSONObject.class);

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分背景
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 450);

        //获取祭典节点
        JSONObject festivalObject = dataObject.getJSONObject("festRecords").getJSONArray("nodes").getJSONObject(timeIndex);
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

        MessageContent base64Content = MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64(imageFile));
        if (MessageType.GROUP.equals(message.getMessageType())) {
            //群聊要添加@用户
            MessageContent atMessageContent = MessageContent.buildAtMessageContent(userId);
            actionApi.sendGroupMessage(groupId, Arrays.asList(atMessageContent, base64Content));
        }else {
            actionApi.sendPrivateMessage(userId, base64Content);
        }
    }

    /**
     * splatoon3活动日程
     * @author ren
     */
    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"活动"}, name = "活动日程")
    public void eventHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
        //发起网络请求获取json数据
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/schedules.json", httpHeaders, JSONObject.class).getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restClient.get("https://splatoon3.ink/data/locale/zh-CN.json", httpHeaders, JSONObject.class);

        //获取背景图片
        File backgroundImage = new File(FileUtils.getAbsolutePath("splatoon/background/bg_good.jpg"));

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //裁剪部分背景
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 600, 674);

        //绘制活动1
        eventMapWriteFromSchedules(imageFile, dataObject.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(0),
                transferObject, 0, 0);

        //绘制活动2
        eventMapWriteFromSchedules(imageFile, dataObject.getJSONObject("eventSchedules").getJSONArray("nodes").getJSONObject(1),
                transferObject, 0, 335);

        MessageContent base64Content = MessageContent.buildImageMessageContentFromBase64(FileUtils.fileToBase64(imageFile));
        if (MessageType.GROUP.equals(message.getMessageType())) {
            //群聊要添加@用户
            MessageContent atMessageContent = MessageContent.buildAtMessageContent(userId);
            actionApi.sendGroupMessage(groupId, Arrays.asList(atMessageContent, base64Content));
        }else {
            actionApi.sendPrivateMessage(userId, base64Content);
        }
    }

    /**
     * 对战地图绘制
     */
    private void regularMapWriteFromSchedules(File imageFile, JSONObject scheduleObject, int x, int y, String modeName) {
        //对战模式获取
        String ruleMode = scheduleObject.getJSONObject("vsRule").getString("name");

        //地图1获取
        String imageUrl1 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(0).getJSONObject("image").getString("url");
        File imageFile1 = getImageFile(imageUrl1, "stages");

        //地图2获取
        String imageUrl2 = scheduleObject.getJSONArray("vsStages")
                .getJSONObject(1).getJSONObject("image").getString("url");
        File imageFile2 = getImageFile(imageUrl2, "stages");

        //绘制对战模式名称
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 42, Color.WHITE,
                ruleModeMap.get(ruleMode),
                x+10, y+40,
                30, 30,
                0,
                imageFile.getAbsolutePath());

        //绘制地图1
        ImageUtils.mergeImageToOtherImage(imageFile, imageFile1, x+75, y, 0.6, imageFile);

        //绘制地图2
        ImageUtils.mergeImageToOtherImage(imageFile, imageFile2, x+325, y, 0.6, imageFile);

        //绘制对战图标
        File iconImage = new File(FileUtils.getAbsolutePath("splatoon/mode/" + modeName + ".png"));
        ImageUtils.mergeImageToOtherImage(imageFile, iconImage, x+290, y+25, 0.5, imageFile);
    }


    /**
     * 绘制打工地图
     */
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
     * 活动地图绘制
     */
    private void eventMapWriteFromSchedules(File imageFile, JSONObject eventObject, JSONObject transferObject, int x, int y) {
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
        if (timePeriods.size() >= 1) {
            ImageUtils.writeWordInImage(imageFile,
                    "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                    "活动时间1：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(0).getString("startTime"), timePeriods.getJSONObject(0).getString("endTime")),
                    x + 95, y + 260,
                    600, 600,
                    0,
                    imageFile.getAbsolutePath());
        }
        //绘制活动时间2
        if (timePeriods.size() >= 2) {
            ImageUtils.writeWordInImage(imageFile,
                    "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                    "活动时间2：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(1).getString("startTime"), timePeriods.getJSONObject(1).getString("endTime")),
                    x + 95, y + 280,
                    600, 600,
                    0,
                    imageFile.getAbsolutePath());
        }
        //绘制活动时间3
        if (timePeriods.size() >= 3) {
            ImageUtils.writeWordInImage(imageFile,
                    "font/sakura.ttf", Font.PLAIN, 20, Color.YELLOW,
                    "活动时间3：" + DateUtils.convertUTCTimeToShowString(timePeriods.getJSONObject(2).getString("startTime"), timePeriods.getJSONObject(2).getString("endTime")),
                    x + 95, y + 300,
                    600, 600,
                    0,
                    imageFile.getAbsolutePath());
        }
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
        ImageUtils.mergeImageToOtherImage(backgroundFile, subFile, x, y, ratio, backgroundFile);
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
                log.error("获取斯普拉遁图片出现异常", e);
            }
        }

        return file;
    }
}
