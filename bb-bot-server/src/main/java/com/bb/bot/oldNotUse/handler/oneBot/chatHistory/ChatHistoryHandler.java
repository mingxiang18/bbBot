package com.bb.bot.oldNotUse.handler.oneBot.chatHistory;

import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.oldNotUse.api.oneBot.ActionApi;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.entity.oneBot.ReceiveMessage;
import com.bb.bot.oldNotUse.event.oneBot.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
//@BootEventHandler(order = 1)
public class ChatHistoryHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(ReceiveMessageEvent event) {
        ReceiveMessage message = event.getData();

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(String.valueOf(message.getMessageId()));
        chatHistory.setUserQq(message.getUserId());
        chatHistory.setGroupId(message.getGroupId());
        chatHistory.setText(message.getMessage());
        chatHistoryMapper.insert(chatHistory);
    }
}
