package com.bb.onebot.handler.qqnt.chatHistory;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.oneBot.ActionApi;
import com.bb.onebot.constant.BotType;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.constant.SyncType;
import com.bb.onebot.database.chatHistory.entity.ChatHistory;
import com.bb.onebot.database.chatHistory.mapper.ChatHistoryMapper;
import com.bb.onebot.entity.qqnt.QqntReceiveMessage;
import com.bb.onebot.event.qqnt.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.stream.Collectors;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler(botType = BotType.QQNT, order = 1)
public class QqntChatHistoryHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息", syncType = SyncType.SYNC)
    public void chatHistoryHandle(ReceiveMessageEvent event) {
        QqntReceiveMessage message = event.getData();

        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setMessageId(message.getRaw().getMsgId());
        chatHistory.setUserQq(message.getSender().getUid());
        if ("group".equals(message.getPeer().getChatType())) {
            chatHistory.setGroupId(message.getPeer().getUid());
        }
        if (!CollectionUtils.isEmpty(message.getRaw().getElements())) {
            String messageContent = message.getRaw().getElements().stream().filter(messageElement -> messageElement.getTextElement() != null).map(messageElement -> {
                return messageElement.getTextElement().getContent();
            }).collect(Collectors.joining(""));
            chatHistory.setText(messageContent);
        }
        chatHistoryMapper.insert(chatHistory);
    }
}
