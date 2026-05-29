package com.bb.bot.handler.chatHistory;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天历史相关命令（merge 后）：
 *
 * <p>M8.3 cutover：</p>
 * <ul>
 *   <li>原 chatHistoryHandle 已删 —— 所有入站消息由 {@link MemoryEventRecorder}
 *       在 BbEventListener 阶段统一落 ai_memory_event。chat_history 表停写。</li>
 *   <li>summary / characteristic 改读 ai_memory_event，兼容转 ChatHistory 给
 *       {@link MessageBuilder} 用。</li>
 *   <li>bot 回复进 ai_memory_event(kind=chat_reply)，不再写 chat_history。</li>
 * </ul>
 *
 * <p>抽取 quizzical-pare 的 {@code runSummary} 复用结构 + AiChatService 错误分类。</p>
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, order = 1, name = "聊天历史记录")
public class BbChatHistoryHandler {

    @Autowired
    private BbReplies bbReplies;

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private PromptProperties promptProperties;

    @Autowired
    private MemoryQueryService memoryQueryService;

    @Autowired
    private MemoryEventRecorder memoryEventRecorder;

    @Value("${chatHistory.summary.chatHistoryNum:100}")
    private int chatHistoryNum;

    // 注：原 @Rule(eventType=MESSAGE) 的 chatHistoryHandle 已删除。
    // 入站消息 100% 由 MemoryEventRecorder.recordInbound 落 ai_memory_event（按 kind 分类）。

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"聊天记录总结", "/聊天记录总结"}, name = "近期聊天记录总结")
    public void chatHistorySummaryHandle(BbReceiveMessage bbReceiveMessage) {
        runSummary(bbReceiveMessage,
                promptProperties.getChatHistory().getSummary(),
                "机器人当前暂不支持聊天记录总结",
                "当前暂无聊天记录可总结");
    }

    public void chatHistoryCharacteristicHandle(BbReceiveMessage bbReceiveMessage) {
        runSummary(bbReceiveMessage,
                promptProperties.getChatHistory().getCharacteristic(),
                "机器人当前暂不支持提取聊天线索",
                "当前暂无聊天记录可提取线索");
    }

    private void runSummary(BbReceiveMessage source,
                            String personality,
                            String unsupportedMsg,
                            String emptyHistoryMsg) {
        if (!aiChatService.isConfigured()) {
            bbReplies.atText(source, unsupportedMsg);
            return;
        }

        List<ChatHistory> history = loadHistoryFromMemory(source);
        if (CollectionUtils.isEmpty(history)) {
            bbReplies.atText(source, emptyHistoryMsg);
            return;
        }

        List<ChatMessage> messages = MessageBuilder.buildSummaryMessages(personality, history);

        String answer;
        try {
            answer = aiChatService.chat(messages, com.bb.bot.common.util.aiChat.provider.ModelTier.LIGHT);
        } catch (AIException e) {
            log.error("AI summary failed (type={}, status={})", e.getErrorType(), e.getHttpStatus(), e);
            bbReplies.atText(source, "AI 调用失败，请稍后再试");
            return;
        }
        if (StringUtils.isBlank(answer)) {
            return;
        }

        // M8.3 cutover：bot 回复进 ai_memory_event(kind=chat_reply)，不再写 chat_history
        memoryEventRecorder.recordOutbound(source, "chat_reply", answer, IdWorker.getIdStr());

        bbReplies.atText(source, "\n" + answer);
    }

    /**
     * M8.3：从 ai_memory_event 拉历史（按 user/group 维度最近 chatHistoryNum 条），
     * 转 ChatHistory 给 {@link MessageBuilder#buildSummaryMessages} 用。
     */
    private List<ChatHistory> loadHistoryFromMemory(BbReceiveMessage msg) {
        List<AiMemoryEvent> events = memoryQueryService.loadRecent(
                msg.getUserId(), msg.getGroupId(), chatHistoryNum);
        return events.stream()
                .map(BbChatHistoryHandler::eventToLegacyChatHistory)
                .collect(Collectors.toList());
    }

    private static ChatHistory eventToLegacyChatHistory(AiMemoryEvent e) {
        ChatHistory ch = new ChatHistory();
        ch.setMessageId(e.getMessageId());
        ch.setUserQq("bot".equals(e.getSource()) ? MessageBuilder.BOT_USER_FLAG : e.getUserId());
        ch.setUserName(e.getUserName());
        ch.setGroupId(e.getGroupId());
        ch.setPrivateUserId(e.getUserId());
        if (StringUtils.isNoneBlank(e.getPayload())) {
            ch.setText(e.getPayload());
        } else {
            ch.setText("[{\"type\":\"text\",\"data\":" + JSON.toJSONString(e.getText() == null ? "" : e.getText()) + "}]");
        }
        ch.setCreateTime(e.getCreatedAt());
        return ch;
    }
}
