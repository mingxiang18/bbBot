package com.bb.bot.handler.aiChat;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.AiChatClient;
import com.bb.bot.common.util.aiChat.ChatGPTContent;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.common.util.aiChat.prompt.PromptRenderer;
import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.MessageContent;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiKeywordAndClue.service.IAiClueService;
import com.bb.bot.database.aiKeywordAndClue.vo.ClueDetail;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.database.chatHistory.service.IChatHistoryService;
import com.bb.bot.database.userConfigInfo.entity.UserConfigValue;
import com.bb.bot.database.userConfigInfo.service.IUserConfigValueService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.bb.MessageUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.Asserts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 聊天事件处理器（merge 后）。
 *
 * <p>结构来自 quizzical-pare 的清晰拆分（decide / load / compose / send），
 * 但所有上下文 IO 走 M8 ai_memory_event 新事件流：</p>
 * <ul>
 *   <li>{@link MemoryQueryService} 读「同 session 内 chat / chat_reply」最近 N 条</li>
 *   <li>{@link MemoryEventRecorder} 写 kind=chat_reply（老 chat_history 表已停写）</li>
 *   <li>{@link com.bb.bot.aiAgent.memory.MemoryCompiler} 把 memory.md 注入 system prompt</li>
 * </ul>
 *
 * <p>LLM 调用两条路径：</p>
 * <ul>
 *   <li>{@code streamEnabled=true} → {@link AiChatClient#askChatGPTStream}（M1 SSE 流式）</li>
 *   <li>{@code streamEnabled=false} → {@link AiChatService#chat}（quizzical 的 provider 抽象，自带错误分类）</li>
 * </ul>
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI聊天")
public class BbAiChatHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    /** 流式路径（M1：SSE + function calling 协议）。 */
    @Autowired
    private AiChatClient aiChatClient;

    /** 阻塞路径（quizzical：AIProvider 抽象 + 错误分类）。 */
    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private IChatHistoryService chatHistoryService;

    @Autowired
    private IAiClueService aiClueService;

    @Autowired
    private IUserConfigValueService userConfigValueService;

    @Autowired
    private PromptProperties promptProperties;

    /** M8 长期记忆三件套。 */
    @Autowired
    private MemoryQueryService memoryQueryService;

    @Autowired
    private MemoryEventRecorder memoryEventRecorder;

    @Autowired
    private com.bb.bot.aiAgent.memory.MemoryCompiler memoryCompiler;

    @Value("${aiChat.autoReplyRate:0.99}")
    private double autoReplyRate;

    @Value("${aiChat.chatHistoryNum:10}")
    private int chatHistoryNum;

    @Value("#{'${aiChat.adminUserIds:}'.split(',')}")
    private List<String> adminUserIds;

    @Value("${chatGPT.streamEnabled:false}")
    private Boolean streamEnabled;

    /** 测试注入用的确定性随机源。 */
    DoubleSupplier randomSource = () -> ThreadLocalRandom.current().nextDouble();

    // =========================================================================
    // 主入口
    // =========================================================================

    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.DEFAULT, name = "ai自动回复")
    public void aiChatHandle(BbReceiveMessage bbReceiveMessage) {
        ReplyDecision decision = decideShouldReply(bbReceiveMessage);
        if (!decision.isShouldReply()) {
            return;
        }

        // M8 cutover：history 从 ai_memory_event 拉，转 ChatHistory 兼容 MessageBuilder
        List<ChatHistory> historyList = loadHistory(bbReceiveMessage);
        ChatHistory replyTarget = findReplyTarget(bbReceiveMessage, historyList);

        // personality = prompts.yml 模板 + clue suffix + memory.md 注入
        String personality = composePersonality(bbReceiveMessage.getUserId(), decision.getClues());

        List<ChatMessage> messages = MessageBuilder.buildContextMessages(
                personality, bbReceiveMessage.getMessageContentList(), historyList, replyTarget);

        if (Boolean.TRUE.equals(streamEnabled)) {
            streamAiReply(bbReceiveMessage, messages);
        } else {
            blockingAiReply(bbReceiveMessage, messages);
        }
    }

    /** 阻塞分支：走 AiChatService（quizzical provider 抽象，带错误分类）。 */
    private void blockingAiReply(BbReceiveMessage msg, List<ChatMessage> messages) {
        String answer;
        try {
            answer = aiChatService.chat(messages);
        } catch (AIException e) {
            log.error("AI provider call failed (type={}, status={}), skipping reply",
                    e.getErrorType(), e.getHttpStatus(), e);
            return;
        }
        if (StringUtils.isBlank(answer)) {
            return;
        }
        List<BbMessageContent> answerMessage = Collections.singletonList(BbMessageContent.buildTextContent(answer));
        persistBotReply(msg, answerMessage);
        sendReply(msg, answerMessage);
    }

    /** 流式分支：走 AiChatClient.askChatGPTStream（M1 SSE）。 */
    private void streamAiReply(BbReceiveMessage msg, List<ChatMessage> messages) {
        // ChatMessage → ChatGPTContent 适配（流式 client 是 M1 时基于旧类型的）
        List<ChatGPTContent> legacy = messages.stream()
                .map(BbAiChatHandler::chatMessageToLegacy)
                .collect(Collectors.toList());

        BbSendMessage envelope = new BbSendMessage(msg);
        MessageStreamSession session = bbMessageApi.startStream(envelope);
        aiChatClient.askChatGPTStream(
                legacy,
                session::appendDelta,
                fullText -> {
                    session.complete();
                    if (StringUtils.isNoneBlank(fullText)) {
                        persistBotReply(msg, Collections.singletonList(BbMessageContent.buildTextContent(fullText)));
                    }
                },
                err -> {
                    log.error("ai 流式回复异常", err);
                    session.fail(err);
                });
    }

    // =========================================================================
    // M8 长期记忆 hooks
    // =========================================================================

    /** M8.3：bot 回复进 ai_memory_event(kind=chat_reply)，不再写老 chat_history。 */
    private void persistBotReply(BbReceiveMessage msg, List<BbMessageContent> answerMessage) {
        String text = answerMessage.stream()
                .filter(c -> c.getData() != null)
                .map(c -> c.getData().toString())
                .reduce("", (a, b) -> a + b);
        memoryEventRecorder.recordOutbound(msg, "chat_reply", text, IdWorker.getIdStr());
    }

    /** M8.3：history 从 ai_memory_event 拉，转 ChatHistory 给 MessageBuilder 用。 */
    private List<ChatHistory> loadHistory(BbReceiveMessage msg) {
        List<AiMemoryEvent> events = memoryQueryService.loadChatContext(
                msg.getUserId(), msg.getGroupId(), msg.getBotType(), chatHistoryNum);
        return events.stream().map(BbAiChatHandler::eventToLegacyChatHistory).collect(Collectors.toList());
    }

    /** Reply 引用消息：在当前批次 history 里找；找不到返回 null。 */
    private ChatHistory findReplyTarget(BbReceiveMessage msg, List<ChatHistory> historyList) {
        Optional<BbMessageContent> reply = msg.getMessageContentList().stream()
                .filter(c -> BbSendMessageType.REPLY.equals(c.getType()) && c.getData() != null)
                .findFirst();
        if (reply.isEmpty()) {
            return null;
        }
        String quotedId = reply.get().getData().toString();
        return historyList.stream()
                .filter(h -> quotedId.equals(h.getMessageId()))
                .findFirst()
                .orElse(null);
    }

    /** M8.6：personality = prompts.yml 模板 + clue suffix + 长期记忆 memory.md 注入。 */
    String composePersonality(String userId, List<String> clues) {
        String base = StringUtils.defaultString(promptProperties.getAiChat().getPersonality());
        if (!CollectionUtils.isEmpty(clues)) {
            Map<String, String> vars = new HashMap<>();
            vars.put("clues", String.join("-", clues));
            base = base + "\n" + PromptRenderer.render(promptProperties.getAiChat().getClueSuffix(), vars);
        }
        // M8.6：把 caller user 的长期记忆 memory.md prepend 到 personality
        try {
            String userMemoryMd = memoryCompiler.ensureCompiledMemory(userId);
            if (StringUtils.isNoneBlank(userMemoryMd)) {
                base = base + "\n\n--- 关于这位用户的长期记忆 ---\n" + userMemoryMd + "\n--- 长期记忆结束 ---\n";
            }
        } catch (Exception e) {
            log.warn("注入 memory.md 失败 user={}", userId, e);
        }
        return base;
    }

    // =========================================================================
    // 决策 / 路由
    // =========================================================================

    /**
     * 判断本轮消息是否需要 AI 回复。私聊必回；群聊@必回；群聊未@时按配置 + 关键词 + 概率门控。
     * 包级可见：方便单元测试在 Mock 服务后直接调用。
     */
    ReplyDecision decideShouldReply(BbReceiveMessage msg) {
        if (MessageType.PRIVATE.equals(msg.getMessageType())) {
            return ReplyDecision.reply(Collections.emptyList());
        }
        if (!isGroupLike(msg)) {
            return ReplyDecision.skip();
        }

        boolean atMe = msg.getAtUserList() != null && msg.getAtUserList().stream()
                .filter(MessageUser::getBotFlag).findFirst().isPresent();
        if (atMe) {
            return ReplyDecision.reply(aiClueService.selectClue(formatForClueLookup(msg)));
        }

        UserConfigValue autoConfig = userConfigValueService.getOne(new LambdaQueryWrapper<UserConfigValue>()
                .eq(UserConfigValue::getGroupId, msg.getGroupId())
                .eq(UserConfigValue::getType, "AI")
                .eq(UserConfigValue::getKeyName, "aiAutoReply")
                .eq(UserConfigValue::getValueName, "1")
                .last("limit 1"));
        if (autoConfig == null) {
            return ReplyDecision.skip();
        }

        List<String> clues = aiClueService.selectClue(formatForClueLookup(msg));
        if (CollectionUtils.isEmpty(clues)) {
            return ReplyDecision.skip();
        }
        double rand = randomSource.getAsDouble();
        log.info("AI 自动回复随机数：{} (阈值 {})", rand, autoReplyRate);
        return rand > autoReplyRate ? ReplyDecision.reply(clues) : ReplyDecision.skip();
    }

    // =========================================================================
    // 线索管理（owner 才能玩）
    // =========================================================================

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"获取聊天线索", "/获取聊天线索"}, name = "获取聊天线索")
    public void chatHistoryClueHandle(BbReceiveMessage bbReceiveMessage) {
        if (!isAdmin(bbReceiveMessage.getUserId())) {
            sendAtText(bbReceiveMessage, "您当前不具备该权限噢");
            return;
        }
        List<ClueDetail> clueDetailList = aiClueService.getClueDetailList();
        sendAtText(bbReceiveMessage, "\n" + JSON.toJSONString(clueDetailList));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?导入线索\\s?"}, name = "导入线索")
    public void importClueHandle(BbReceiveMessage bbReceiveMessage) {
        Matcher matcher = Pattern.compile("导入线索([\\s\\S]*)").matcher(bbReceiveMessage.getMessage());
        String clue = matcher.find() ? matcher.group(1) : null;

        List<ClueDetail> clueDetailList;
        try {
            Asserts.notNull(clue, "线索不能为空");
            clueDetailList = JSON.parseArray(clue, ClueDetail.class);
        } catch (Exception e) {
            sendAtText(bbReceiveMessage, "线索格式不正确");
            return;
        }

        aiClueService.importGroupClue(bbReceiveMessage.getGroupId(), clueDetailList);
        sendAtText(bbReceiveMessage, "导入成功");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?删除线索\\s?"}, name = "删除线索")
    public void deleteClueHandle(BbReceiveMessage bbReceiveMessage) {
        Matcher matcher = Pattern.compile("^/?删除线索(\\d+)").matcher(bbReceiveMessage.getMessage());
        String clueId = matcher.find() ? matcher.group(1) : null;

        try {
            aiClueService.deleteClue(Long.parseLong(clueId));
        } catch (Exception e) {
            log.error("删除线索失败", e);
            sendAtText(bbReceiveMessage, "删除失败，格式不正确");
            return;
        }
        sendAtText(bbReceiveMessage, "删除成功");
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void sendReply(BbReceiveMessage source, List<BbMessageContent> answerMessage) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(answerMessage);
        bbMessageApi.sendMessage(out);
    }

    private void sendAtText(BbReceiveMessage source, String text) {
        BbSendMessage out = new BbSendMessage(source);
        out.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(source.getUserId()),
                BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(out);
    }

    private boolean isGroupLike(BbReceiveMessage msg) {
        return MessageType.GROUP.equals(msg.getMessageType())
                || MessageType.CHANNEL.equals(msg.getMessageType());
    }

    private boolean isAdmin(String userId) {
        if (CollectionUtils.isEmpty(adminUserIds)) {
            return false;
        }
        return adminUserIds.stream().map(String::trim).filter(StringUtils::isNotBlank)
                .anyMatch(id -> id.equals(userId));
    }

    private String formatForClueLookup(BbReceiveMessage msg) {
        String prefix = msg.getSender() == null ? "" : msg.getSender().getNickname() + "：";
        return prefix + msg.getMessage();
    }

    // ---- 类型适配 ----

    /** ChatMessage（quizzical）→ ChatGPTContent（M1 流式 client 用的旧类型）。 */
    private static ChatGPTContent chatMessageToLegacy(ChatMessage m) {
        ChatGPTContent c = new ChatGPTContent();
        // ChatMessage.Role 是 enum，ChatGPTContent 的 role 是 String 常量
        switch (m.getRole()) {
            case SYSTEM:    c.setRole(ChatGPTContent.SYSTEM_ROLE); break;
            case ASSISTANT: c.setRole(ChatGPTContent.ASSISTANT_ROLE); break;
            case USER:
            default:        c.setRole(ChatGPTContent.USER_ROLE); break;
        }
        // 把 MessageContent 列表展开成 ChatGPTContent.content（List<Map>）
        List<Map<String, Object>> parts = new ArrayList<>();
        if (m.getContents() != null) {
            for (MessageContent mc : m.getContents()) {
                if (mc == null) continue;
                switch (mc.getType()) {
                    case TEXT:
                        parts.add(ChatGPTContent.buildTextContent(StringUtils.defaultString(mc.getValue())));
                        break;
                    case NET_IMAGE:
                        parts.add(ChatGPTContent.buildNetImageContent(mc.getValue()));
                        break;
                    case BASE64_IMAGE:
                        parts.add(ChatGPTContent.buildBase64ImageContent(mc.getValue()));
                        break;
                }
            }
        }
        // 若只有一段纯文本，content 直接是 String（兼容现有 LLM provider）
        if (parts.size() == 1 && "text".equals(parts.get(0).get("type"))) {
            c.setContent(parts.get(0).get("text"));
        } else if (!parts.isEmpty()) {
            c.setContent(parts);
        } else {
            c.setContent("");
        }
        return c;
    }

    /** AiMemoryEvent → ChatHistory：保留 MessageBuilder 既有签名不动。 */
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
