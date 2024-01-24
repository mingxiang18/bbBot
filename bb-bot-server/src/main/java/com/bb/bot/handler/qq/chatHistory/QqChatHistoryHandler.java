package com.bb.bot.handler.qq.chatHistory;

import com.bb.bot.annotation.BootEventHandler;
import com.bb.bot.annotation.Rule;
import com.bb.bot.api.qq.QqMessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.EventType;
import com.bb.bot.constant.SyncType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.bot.entity.qq.QqMessage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler(botType = BotType.QQ, order = 1)
public class QqChatHistoryHandler {

    @Autowired
    private QqMessageApi qqMessageApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(QqMessage event) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(String.valueOf(event.getId()));
        chatHistory.setUserQq(event.getAuthor().getId());
        chatHistory.setGroupId(event.getChannelId());
        chatHistory.setText(event.getContent());
        chatHistoryMapper.insert(chatHistory);
    }
}
