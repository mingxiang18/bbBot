package com.bb.bot.oldNotUse.handler.qq.hello;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.oldNotUse.api.qq.QqMessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.QqMessage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 打招呼事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.QQ)
public class QqHelloHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"你好"}, name = "打招呼")
    public void helloHandle(QqMessage event) {
        ChannelMessage channelMessage = new ChannelMessage();
        channelMessage.setContent("你好呀");
        channelMessage.setMsgId(event.getId());
        //channelMessage.setImage("https://raw.githubusercontent.com/mingxiang18/bbBot/master/bb-bot-server/src/main/resources/static/splatoon/background/1698202998352.png");
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }
}
