package com.bb.onebot.handler.hello;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.ActionApi;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.MessageType;
import com.bb.onebot.constant.RuleType;
import com.bb.onebot.entity.ReceiveMessage;
import com.bb.onebot.event.ReceiveMessageEvent;
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
