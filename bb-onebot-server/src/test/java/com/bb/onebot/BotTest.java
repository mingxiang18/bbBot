package com.bb.onebot;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.onebot.database.japaneseLearn.entity.JapaneseFifty;
import com.bb.onebot.database.japaneseLearn.mapper.JapaneseFiftyMapper;
import com.bb.onebot.handler.oneBot.aiChat.AiChatHandler;
import com.bb.onebot.util.DateUtils;
import com.bb.onebot.util.FileUtils;
import com.bb.onebot.util.ImageUtils;
import com.bb.onebot.util.RestClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

@SpringBootTest
public class BotTest {

    @Autowired
    private RestClient restClient;

    @Autowired
    private AiChatHandler aiChatHandler;

    @Autowired
    private JapaneseFiftyMapper japaneseFiftyMapper;

    @Test
    public void randomJapaneseFifty() throws Exception {
        boolean loop = true;

        while (loop) {
            Integer jpFiftyCount = japaneseFiftyMapper.selectCount(null);
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
        InputStream fileInputStream = restClient.getFileInputStream("https://splatoon3.ink/assets/event.6d1af9f3.svg");

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
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/schedules.json", JSONObject.class).getJSONObject("data");

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
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/festivals.json", JSONObject.class).getJSONObject("JP").getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restClient.get("https://splatoon3.ink/data/locale/zh-CN.json", JSONObject.class);

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
        JSONObject dataObject = restClient.get("https://splatoon3.ink/data/schedules.json", JSONObject.class).getJSONObject("data");
        //发起网络请求获取中文json数据
        JSONObject transferObject = restClient.get("https://splatoon3.ink/data/locale/zh-CN.json", JSONObject.class);

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
