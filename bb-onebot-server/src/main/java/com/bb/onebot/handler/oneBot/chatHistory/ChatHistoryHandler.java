package com.bb.onebot.handler.oneBot.chatHistory;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.SyncType;
import com.bb.onebot.database.chatHistory.entity.ChatHistory;
import com.bb.onebot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.onebot.entity.oneBot.ReceiveMessage;
import com.bb.onebot.event.oneBot.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler(order = 1)
public class ChatHistoryHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(ReceiveMessageEvent event) {
        ReceiveMessage message = event.getData();

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(message.getMessageId());
        chatHistory.setUserQq(message.getUserId());
        chatHistory.setGroupId(message.getGroupId());
        chatHistory.setText(message.getMessage());
        chatHistoryMapper.insert(chatHistory);
    }
}
