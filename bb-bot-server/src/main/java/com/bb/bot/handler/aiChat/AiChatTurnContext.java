package com.bb.bot.handler.aiChat;

import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class AiChatTurnContext {

    private final BbReceiveMessage message;
    private final List<BbMessageContent> currentContent;
    private final List<ChatHistory> historyList;
    private final ChatHistory replyTarget;
    private final String currentText;
    private final boolean directTrigger;
    private final boolean autoReply;

    public List<BbMessageContent> currentContent() {
        return currentContent == null ? Collections.emptyList() : currentContent;
    }

    public List<ChatHistory> historyList() {
        return historyList == null ? Collections.emptyList() : historyList;
    }
}
