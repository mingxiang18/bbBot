package com.bb.bot.handler.bb.chatHistory;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.SyncType;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.entity.common.BbReceiveMessage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, order = 1)
public class BbChatHistoryHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(BbReceiveMessage bbReceiveMessage) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(bbReceiveMessage.getMessageId());
        chatHistory.setUserQq(bbReceiveMessage.getUserId());
        chatHistory.setGroupId(bbReceiveMessage.getGroupId());
        chatHistory.setText(bbReceiveMessage.getMessage());
        chatHistoryMapper.insert(chatHistory);
    }
}
