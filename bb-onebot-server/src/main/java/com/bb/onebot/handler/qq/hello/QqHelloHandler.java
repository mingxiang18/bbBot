package com.bb.onebot.handler.qq.hello;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.qq.QqMessageApi;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.RuleType;
import com.bb.onebot.entity.qq.ChannelMessage;
import com.bb.onebot.entity.qq.QqMessage;
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
        qqMessageApi.sendChannelMessage(event.getChannelId(), channelMessage);
    }
}
