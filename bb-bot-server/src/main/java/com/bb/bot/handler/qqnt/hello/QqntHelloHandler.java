package com.bb.bot.handler.qqnt.hello;

import com.bb.bot.annotation.BootEventHandler;
import com.bb.bot.annotation.Rule;
import com.bb.bot.api.qqnt.MessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.EventType;
import com.bb.bot.constant.RuleType;
import com.bb.bot.entity.qqnt.QqntReceiveMessage;
import com.bb.bot.entity.qqnt.SendMessageElement;
import com.bb.bot.event.qqnt.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * 打招呼事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.QQNT)
public class QqntHelloHandler {

    @Autowired
    private MessageApi messageApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"你好"}, name = "打招呼")
    public void helloHandle(ReceiveMessageEvent event) {
        QqntReceiveMessage message = event.getData();

        List<SendMessageElement> messageElementList = new ArrayList<>();
        messageElementList.add(SendMessageElement.buildTextMessage("你好呀"));

        messageApi.sendMessage(message.getPeer(), messageElementList);
    }
}
