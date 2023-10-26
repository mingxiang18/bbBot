package com.bb.onebot.handler;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
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
import com.bb.onebot.util.FileUtils;
import com.bb.onebot.util.ImageUtils;
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
@BootEventHandler
public class FortuneHandler {

    @Autowired
    private ActionApi actionApi;

    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.MATCH, keyword = {"抽签", "运势"}, name = "抽签")
    public void helloHandle(ReceiveMessageEvent event) {
        //接收的消息内容
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        //从goodLuck.json随机一个运气状态
        String luckJson = new String(FileUtils.getFile("fortune/goodLuck.json"), StandardCharsets.UTF_8);
        JSONArray luckArray = JSON.parseObject(luckJson).getJSONArray("types_of");
        JSONObject luckObject = (JSONObject) RandomUtil.randomEle(luckArray);

        //运气号码
        Integer luckNumber = luckObject.getInteger("good-luck");
        //运气名
        String luckName = luckObject.getString("name");
        log.info("用户: " + userId + ", 抽取抽取到运气号码" + luckNumber);

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
     * 本地测试生成图片方法
     * @author ren
     */
    public static void main(String[] args) {
        //随机选取签的背景图片
        File imageFile = new File("/D:/develop/bot/bbBot/bb-onebot-server/src/main/resources/static/tmp/" + String.valueOf(System.currentTimeMillis()) + ".png");
        File backgroundImage = new File("/D:/develop/bot/bbBot/bb-onebot-server/src/main/resources/static/fortune/img/frame_1.png");
        //绘制签的运气文字
        ImageUtils.writeWordInImage(backgroundImage,
                "/D:/develop/bot/bbBot/bb-onebot-server/src/main/resources/static/font/sakura.ttf", Font.BOLD, 35, Color.WHITE,
                "大吉",
                110, 110,
                50, 50,
                0,
                imageFile.getAbsolutePath());
        ImageUtils.writeWordInImage(imageFile,
                "/D:/develop/bot/bbBot/bb-onebot-server/src/main/resources/static/font/sakura.ttf", Font.PLAIN, 21, Color.BLACK,
                "与人接触可丰富内心，亲切待人是幸运之钥",
                120, 190,
                50, 450,
                1,
                imageFile.getAbsolutePath());
    }
}
