package com.bb.bot.handler.oneBot.hello;

import com.bb.bot.annotation.BootEventHandler;
import com.bb.bot.annotation.Rule;
import com.bb.bot.api.oneBot.ActionApi;
import com.bb.bot.constant.EventType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.constant.RuleType;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import com.bb.bot.event.oneBot.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 打招呼事件处理器
 * @author ren
 */
@BootEventHandler
public class HelloHandler {

    @Autowired
    private ActionApi actionApi;

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH, keyword = {"你好"}, name = "打招呼")
    public void helloHandle(ReceiveMessageEvent event) {
        ReceiveMessage message = event.getData();
        String groupId = message.getGroupId();
        String userId = message.getUserId();

        if (MessageType.GROUP.equals(message.getMessageType())) {
            actionApi.sendGroupMessage(groupId, "你好呀");
        }else {
            actionApi.sendPrivateMessage(userId, "你好呀");
        }
    }
}
