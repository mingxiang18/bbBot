package com.bb.onebot.handler;

import com.bb.onebot.annotation.BootEventHandler;
import com.bb.onebot.annotation.Rule;
import com.bb.onebot.api.ActionApi;
import com.bb.onebot.constant.EventType;
import com.bb.onebot.database.chatHistory.entity.ChatHitstory;
import com.bb.onebot.database.chatHistory.mapper.ChatHitstoryMapper;
import com.bb.onebot.entity.ReceiveMessage;
import com.bb.onebot.event.ReceiveMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 聊天历史记录处理器
 * 用于记录所有聊天消息
 * @author ren
 */
@BootEventHandler
public class ChatHistoryHandler {

    @Autowired
    private ActionApi actionApi;

    @Autowired
    private ChatHitstoryMapper chatHitstoryMapper;

    @Rule(eventType = EventType.MESSAGE, name = "聊天历史记录, 用于记录所有聊天消息")
    public void chatHistoryHandle(ReceiveMessageEvent event) {
        ReceiveMessage message = event.getData();

        ChatHitstory chatHitstory = new ChatHitstory();
        chatHitstory.setMessageId(message.getMessageId());
        chatHitstory.setUserQq(message.getUserId());
        chatHitstory.setGroupId(message.getGroupId());
        chatHitstory.setText(message.getMessage());
        chatHitstoryMapper.insert(chatHitstory);
    }
}
