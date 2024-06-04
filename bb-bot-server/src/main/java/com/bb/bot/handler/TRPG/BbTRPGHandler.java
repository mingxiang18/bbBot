package com.bb.bot.handler.TRPG;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

/**
 * 跑团事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB)
public class BbTRPGHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {DiceRoller.COMMAND_REG}, name = "跑团骰子投掷")
    public void diceRollerHandle(BbReceiveMessage bbReceiveMessage) {
        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(DiceRoller.parseAndRoll(bbReceiveMessage.getMessage()))));
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
