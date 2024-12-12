package com.bb.bot.api;

import com.bb.bot.api.oneBot.OneBotMessageApi;
import com.bb.bot.api.qq.QqToBbMessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 发起动作请求Api
 * @author ren
 */
@Component
public class BbMessageApiImpl implements BbMessageApi{

    @Autowired
    private OneBotMessageApi oneBotMessageApi;

    @Autowired
    private QqToBbMessageApi qqToBbMessageApi;

    /**
     * 发送消息
     */
    public void sendMessage(BbSendMessage bbSendMessage) {
        //安装机器人类型调用不同的api
        if (BotType.QQ.equals(bbSendMessage.getBotType())) {
            qqToBbMessageApi.sendMessage(bbSendMessage);
        }else if (BotType.ONEBOT.equals(bbSendMessage.getBotType())) {
            oneBotMessageApi.sendMessage(bbSendMessage);
        }
    };
}
