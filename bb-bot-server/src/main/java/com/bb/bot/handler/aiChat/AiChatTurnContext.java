package com.bb.bot.handler.aiChat;

import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/** 单轮聊天输入：集中保存当前消息、历史与引用目标，供路由和图片输入共用。 */
@Getter
@Builder
public class AiChatTurnContext {

    private final BbReceiveMessage message;
    private final List<BbMessageContent> currentContent;
    private final List<ChatHistory> historyList;
    private final ChatHistory replyTarget;
    private final String currentText;
    /** 私聊、@机器人等直接触发；与群聊概率插话区分。 */
    private final boolean directTrigger;
    private final boolean autoReply;

    public List<BbMessageContent> currentContent() {
        return currentContent == null ? Collections.emptyList() : currentContent;
    }

    public List<ChatHistory> historyList() {
        return historyList == null ? Collections.emptyList() : historyList;
    }
}
