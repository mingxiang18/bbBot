package com.bb.onebot.handler.qq.fortune;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.qq.QqMessageApi;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.RuleType;
import com.bb.onebot.entity.qq.ChannelMessage;
import com.bb.onebot.entity.qq.QqMessage;
import com.bb.onebot.util.FileUtils;
import com.bb.onebot.util.ImageUploadClient;
import com.bb.onebot.util.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 抽取/运势事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.QQ)
public class QqFortuneHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private ImageUploadClient imageUploadClient;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"/抽签"}, name = "抽签")
    public void helloHandle(QqMessage event) {
        //随机抽取运气内容
        String luckContentJson = new String(FileUtils.getFile("fortune/copywriting.json"), StandardCharsets.UTF_8);
        List luckContentArray = JSON.parseObject(luckContentJson).getJSONArray("copywriting").stream().collect(Collectors.toList());
        JSONObject luckContentObject = (JSONObject) RandomUtil.randomEle(luckContentArray);
        String luckContent = luckContentObject.getString("content");

        //获取运气内容中的运气号码对应的名称
        String luckJson = new String(FileUtils.getFile("fortune/goodLuck.json"), StandardCharsets.UTF_8);
        JSONArray luckArray = JSON.parseObject(luckJson).getJSONArray("types_of");
        String luckName = luckArray.stream().filter(goodLuck -> {
            return luckContentObject.getInteger("good-luck") == ((JSONObject) goodLuck).getInteger("good-luck");
        }).map(goodLuck -> ((JSONObject) goodLuck).getString("name")).findFirst().get();
        log.info("用户: " + event.getAuthor().getUsername() + ", 抽取到签：" + luckName);

        //随机选取签的背景图片
        File backgroundImage = FileUtils.getRandomFileFromFolder("fortune/img");

        //生成临时图片文件
        File imageFile =  new File(FileUtils.getAbsolutePath("tmp/" + System.currentTimeMillis() + ".png"));
        //绘制签的运气文字
        ImageUtils.writeWordInImage(backgroundImage,
                "font/sakura.ttf", Font.BOLD, 35, Color.WHITE,
                luckName,
                100, 110,
                200, 50,
                0,
                imageFile.getAbsolutePath());
        //绘制签的内容文字
        ImageUtils.writeWordInImage(imageFile,
                "font/sakura.ttf", Font.PLAIN, 21, Color.BLACK,
                luckContent,
                120, 190,
                50, 450,
                1,
                imageFile.getAbsolutePath());


        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent(ChannelMessage.buildAtMessage(event.getAuthor().getId()));
        channelMessage.setFile(imageFile);
        channelMessage.setImage(imageUploadClient.uploadImage(imageFile));
        channelMessage.setMsgId(event.getId());
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }
}
