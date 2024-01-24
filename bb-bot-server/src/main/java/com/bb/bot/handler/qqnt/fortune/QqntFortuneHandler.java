package com.bb.bot.handler.qqnt.fortune;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.annotation.BootEventHandler;
import com.bb.bot.annotation.Rule;
import com.bb.bot.api.qqnt.MessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.EventType;
import com.bb.bot.constant.RuleType;
import com.bb.bot.entity.qqnt.QqntReceiveMessage;
import com.bb.bot.entity.qqnt.SendMessageElement;
import com.bb.bot.event.qqnt.ReceiveMessageEvent;
import com.bb.bot.util.FileUtils;
import com.bb.bot.util.ImageUtils;
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
@BootEventHandler(botType = BotType.QQNT)
public class QqntFortuneHandler {

    @Autowired
    private MessageApi messageApi;

    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"抽签", "运势"}, name = "抽签")
    public void helloHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        QqntReceiveMessage message = event.getData();

        //从goodLuck.json随机一个运气状态
        String luckJson = new String(FileUtils.getFile("fortune/goodLuck.json"), StandardCharsets.UTF_8);
        JSONArray luckArray = JSON.parseObject(luckJson).getJSONArray("types_of");
        JSONObject luckObject = (JSONObject) RandomUtil.randomEle(luckArray);

        //运气号码
        Integer luckNumber = luckObject.getInteger("good-luck");
        //运气名
        String luckName = luckObject.getString("name");
        log.info("用户: " + message.getSender().getUid() + ", 抽取抽取到运气号码" + luckNumber);

        //运气内容
        String luckContentJson = null;
        //根据不同的运气选取不同的抽签文件
        if (luckNumber > 0) {
            //如果大于0，表示好运
            luckContentJson = new String(FileUtils.getFile("fortune/copywriting.json"), StandardCharsets.UTF_8);
        }else if (luckNumber < 0) {
            //如果小于0，表示坏运气
            luckContentJson = new String(FileUtils.getFile("fortune/copywriting-bad.json"), StandardCharsets.UTF_8);
        }else {
            //如果等于0，表示特殊签
            luckContentJson = new String(FileUtils.getFile("fortune/copywriting-on.json"), StandardCharsets.UTF_8);
        }

        //随机抽取运气内容
        List luckContentArray = JSON.parseObject(luckContentJson).getJSONArray("copywriting")
                .stream().filter(luckContent -> {
                    if (luckNumber > 0) {
                        JSONObject luckContentObject = (JSONObject) luckContent;
                        return luckNumber == luckContentObject.getInteger("good-luck");
                    }else {
                        return true;
                    }
                }).collect(Collectors.toList());
        JSONObject luckContentObject = (JSONObject) RandomUtil.randomEle(luckContentArray);
        String luckContent = luckContentObject.getString("content");

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

        messageApi.sendMessage(message.getPeer(), SendMessageElement.buildImgMessage(imageFile.getAbsolutePath()));
    }
}
