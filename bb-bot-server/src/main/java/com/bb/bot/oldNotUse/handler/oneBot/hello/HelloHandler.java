package com.bb.bot.oldNotUse.handler.oneBot.hello;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.oldNotUse.api.oneBot.ActionApi;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.MessageType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import com.bb.bot.oldNotUse.event.oneBot.ReceiveMessageEvent;
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
