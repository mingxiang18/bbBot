package com.bb.bot.handler.bb.hello;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.common.BbMessageContent;
import com.bb.bot.entity.common.BbReceiveMessage;
import com.bb.bot.entity.common.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

/**
 * 打招呼事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB)
public class BbHelloHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"你好"}, name = "打招呼")
    public void helloHandle(BbReceiveMessage bbReceiveMessage) {
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent("你好呀")));
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
