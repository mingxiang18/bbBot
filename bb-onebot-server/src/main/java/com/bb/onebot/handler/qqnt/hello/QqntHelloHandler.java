package com.bb.onebot.handler.qqnt.hello;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.qqnt.MessageApi;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.RuleType;
import com.bb.onebot.entity.qqnt.QqntReceiveMessage;
import com.bb.onebot.entity.qqnt.SendMessageElement;
import com.bb.onebot.event.qqnt.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
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
