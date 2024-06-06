package com.bb.bot.handler.fortune;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.util.imageUpload.ImageUploadApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 抽取/运势事件处理器
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "抽签")
public class BbFortuneHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ImageUploadApi imageUploadApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"/抽签", "抽签"}, name = "抽签")
    public void fortuneHandle(BbReceiveMessage bbReceiveMessage) {
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
        log.info("用户: " + bbReceiveMessage.getUserId() + ", 抽取到签：" + luckName);

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

        //发送消息
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
            BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
            BbMessageContent.buildLocalImageMessageContent(imageFile))
        );
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
