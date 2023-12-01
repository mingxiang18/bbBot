package com.bb.onebot.handler.oneBot.poke;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.config.BotConfig;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.entity.oneBot.MessageContent;
import com.bb.onebot.entity.oneBot.ReceiveNotice;
import com.bb.onebot.event.oneBot.ReceiveNoticeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 戳一戳事件处理者
 * @author ren
 */
@Slf4j
@BootEventHandler
public class PokeHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private BotConfig botConfig;

    @Rule(eventType = EventType.NOTICE, name = "戳一戳反馈")
    public void pokeHandle(ReceiveNoticeEvent event) {
        ReceiveNotice notice = event.getData();
        //如果收到戳一戳提醒且接收qq号为自己
        if ("poke".equals(notice.getSubType()) && botConfig.getQq().equals(notice.getTargetId())) {
            String groupId = notice.getGroupId();
            String userId = notice.getUserId();

            //反馈一个戳一戳
            MessageContent pokeMessage = MessageContent.buildPokeMessageContent(userId);
            if (groupId != null) {
                actionApi.sendGroupMessage(groupId, pokeMessage);
            } else {
                actionApi.sendPrivateMessage(userId, pokeMessage);
            }
        }
    }
}
