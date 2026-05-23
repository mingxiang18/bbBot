package com.bb.bot.handler.aiChat;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bb.bot.aiAgent.core.AgentRunRegistry;
import com.bb.bot.aiAgent.core.AiToolExecutor;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.aiAgent.core.RunHandle;
import com.bb.bot.aiAgent.fs.AgentFileStore;
import com.bb.bot.aiAgent.tools.AgentReplySink;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.aiAgent.skills.SkillRegistry;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.prompt.PromptProperties;
import com.bb.bot.common.util.aiChat.prompt.PromptRenderer;
import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AiCallContext;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelRouter;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.common.util.aiChat.provider.StreamHandler;
import com.bb.bot.common.util.aiChat.provider.ToolCall;
import com.bb.bot.common.util.aiChat.provider.ToolDefinition;
import com.bb.bot.common.util.aiChat.provider.ToolLoopExecutor;
import com.bb.bot.connection.bb.BbWebSocketServer;
import com.bb.bot.constant.BbCapability;
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
 * 统一 AI 处理器：一个 handler 同时承担「聊天」和「干活」。
 *
 * <p>不再需要 {@code agent } 前缀显式切换。直接对话（私聊 / @机器人）时把已注册的
 * 工具一并交给模型，由模型自己通过 function calling 决定是闲聊还是调用工具完成任务
 * ——模型的工具决策本身就是路由器，无需额外的意图分类调用。</p>
 *
 * <ul>
 *   <li>直接对话 + {@code aiChat.toolsEnabled} → 走 {@link ToolLoopExecutor} 工具循环，
 *       system prompt = 人格 + 工具引导 + 技能目录 + 长期记忆，全程流式</li>
 *   <li>群聊概率自动回复 → 纯聊天（不挂工具，省 token、防误触发）</li>
 * </ul>
 *
 * <p>历史上为单独的 {@code BbAiAgentHandler}，已合并至此；老的 {@code agent } 前缀
 * 仍被识别并剥离，保证向后兼容。</p>
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI聊天")
public class BbAiChatHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    /** 阻塞 + 流式都走 AiChatService（M9 重构后唯一入口）。 */
    @Autowired
    private AiChatService aiChatService;

    /** 廉价模型分类器：判断本轮是轻量还是重度任务，决定走 LIGHT 还是 CHAT。 */
    @Autowired
    private ModelRouter modelRouter;

    /** 月度限额拦截：超额硬阻断。 */
    @Autowired
    private com.bb.bot.common.util.aiChat.billing.QuotaGuard quotaGuard;

    /** 全局每日 token 兜底。 */
    @Autowired
    private com.bb.bot.common.util.aiChat.billing.GlobalUsageGuard globalUsageGuard;

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

    /** 工具循环编排 + 注册表 + 执行器 + 技能目录（合并 agent 能力后引入）。 */
    @Autowired
    private ToolLoopExecutor toolLoopExecutor;

    @Autowired
    private AiToolRegistry toolRegistry;

    @Autowired
    private AiToolExecutor toolExecutor;

    @Autowired
    private SkillRegistry skillRegistry;

    /** steering 支撑：记录运行中的 agent，用户中途追加消息时并入而非另起。 */
    @Autowired
    private AgentRunRegistry agentRunRegistry;

    /** 入站文件 / 图片落盘到每用户目录，供模型用 file_read 读取。 */
    @Autowired
    private AgentFileStore agentFileStore;

    @Value("${aiChat.autoReplyRate:0.99}")
    private double autoReplyRate;

    @Value("${aiChat.chatHistoryNum:10}")
    private int chatHistoryNum;

    @Value("#{'${aiChat.adminUserIds:}'.split(',')}")
    private List<String> adminUserIds;

    /** M9.9：迁到 aiChat.streamEnabled，跟 provider 配置解耦。chatGPT.streamEnabled 仍兼容（fallback）。 */
    @Value("${aiChat.streamEnabled:${chatGPT.streamEnabled:false}}")
    private Boolean streamEnabled;

    /** 是否在直接对话时给模型挂载工具（让它能干活）。关掉则退化为纯聊天机器人。 */
    @Value("${aiChat.toolsEnabled:true}")
    private boolean toolsEnabled;

    /** 工具调用循环硬上限，复用 agent 配置。 */
    @Value("${aiAgent.maxSteps:10}")
    private int maxSteps;

    /** 挂载工具时追加到 system prompt 的工具使用引导。 */
    @Value("${aiChat.toolGuidance:你不仅能聊天，还能调用工具真正帮用户干活——" +
            "查实时/外部信息、读写文件、跑命令、联网搜索等。当用户明确要求执行某个操作、" +
            "查询实时或外部信息、或处理文件时，直接调用合适的工具完成；普通闲聊就正常对话，" +
            "不要为了用工具而用工具。把工具结果用你一贯的人格讲给用户，不要原样贴 JSON。}")
    private String toolGuidance;

    /** 测试注入用的确定性随机源。 */
    DoubleSupplier randomSource = () -> ThreadLocalRandom.current().nextDouble();

    /** 老的 agent 前缀，向后兼容：识别并剥离，不再作为开启工具的必要条件。 */
    private static final String[] LEGACY_AGENT_PREFIXES = {"agent ", "/agent ", "Agent "};

    // =========================================================================
    // 主入口
    // =========================================================================

    @Rule(eventType = EventType.MESSAGE, ruleType = RuleType.DEFAULT, name = "ai自动回复")
    public void aiChatHandle(BbReceiveMessage bbReceiveMessage) {
        ReplyDecision decision = decideShouldReply(bbReceiveMessage);
        if (!decision.isShouldReply()) {
            return;
        }

        // 全局每日 token 兜底：达上限暂停所有 AI 回复，防止异常流量烧 token（直接对话才提示，避免刷屏）。
        if (globalUsageGuard.isOverDailyLimit()) {
            log.warn("全局每日 token 已达上限（{}/{}），暂停 AI 回复 user={}",
                    globalUsageGuard.tokensToday(), globalUsageGuard.dailyLimit(), bbReceiveMessage.getUserId());
            if (decision.isDirectTrigger()) {
                sendAtText(bbReceiveMessage, "今日 AI 调用量已达系统上限，请明天再试。");
            }
            return;
        }

        // 月度限额：超额硬阻断（所有人含 owner）。命令类 handler 不经此入口，申请/审批不受影响。
        if (quotaGuard.isOverLimit(bbReceiveMessage.getUserId(), bbReceiveMessage.getBotType())) {
            // 仅直接对话（私聊 / @我）才回提示，群里概率自动回复直接静默跳过，避免刷屏
            if (decision.isDirectTrigger()) {
                com.bb.bot.common.util.aiChat.billing.QuotaGuard.QuotaStatus st =
                        quotaGuard.status(bbReceiveMessage.getUserId(), bbReceiveMessage.getBotType());
                sendAtText(bbReceiveMessage, String.format(
                        "本月 AI 额度已用完（已用 ¥%s / 额度 ¥%s）。发『/额度申请 理由』可请求管理员重置。",
                        st.getSpent().toPlainString(), st.getLimit().toPlainString()));
            }
            return;
        }

        // M8 cutover：history 从 ai_memory_event 拉，转 ChatHistory 兼容 MessageBuilder
        List<ChatHistory> historyList = loadHistory(bbReceiveMessage);
        ChatHistory replyTarget = findReplyTarget(bbReceiveMessage, historyList);
        List<BbMessageContent> currentContent = stripLegacyAgentPrefix(bbReceiveMessage.getMessageContentList());

        // 闲聊还是干活：直接对话用廉价模型分类；群聊概率自动回复一律按闲聊（轻模型、不挂工具）。
        // 干活(CHAT) → 重模型 + 工具循环；闲聊(LIGHT) → 轻模型纯聊天。
        ModelTier tier;
        if (decision.isDirectTrigger()) {
            ChatMessage ask = MessageBuilder.buildAskMessage(currentContent, replyTarget);
            tier = modelRouter.classify(Collections.singletonList(ask));
        } else {
            tier = ModelTier.LIGHT;
        }
        boolean useTools = toolsEnabled && tier == ModelTier.CHAT;

        String personality = composePersonality(bbReceiveMessage.getUserId(), decision.getClues(), useTools);

        // 挂工具时把入站文件 / 图片落盘到该用户目录：文件类附件的 data 被改写成本地路径，
        // 模型即可经 file_read 读取真实内容（群聊概率回复不挂工具、无 file_read，跳过）。
        if (useTools) {
            agentFileStore.materializeInbound(
                    bbReceiveMessage.getUserId(), bbReceiveMessage.getMessageId(), currentContent);
        }

        List<ChatMessage> messages = MessageBuilder.buildContextMessages(
                personality, currentContent, historyList, replyTarget);

        // 把调用方身份带到 provider 层，供 token 用量按 用户/平台/会话 归属
        AiCallContext.setIdentity(
                bbReceiveMessage.getUserId(),
                bbReceiveMessage.getBotType(),
                "chat-" + bbReceiveMessage.getMessageId());
        try {
            if (useTools) {
                toolLoopReply(bbReceiveMessage, messages);
            } else if (Boolean.TRUE.equals(streamEnabled)) {
                streamAiReply(bbReceiveMessage, messages, tier);
            } else {
                blockingAiReply(bbReceiveMessage, messages, tier);
            }
        } finally {
            AiCallContext.clearIdentity();
        }
    }

    /**
     * 工具循环分支：把已注册工具交给模型，跑 function calling 循环，全程流式。
     * 模型若判断只是闲聊则不会调用任何工具，行为与纯聊天一致。
     *
     * <p>steering：同一会话已有运行中的 agent 时，本条消息并入那一轮（中途改方向 /
     * 追加需求），不另起回复。run 收尾的极小竞态窗口里漏接的消息会被补派。</p>
     */
    private void toolLoopReply(BbReceiveMessage msg, List<ChatMessage> messages) {
        String sessionKey = AgentRunRegistry.sessionKey(msg.getBotType(), msg.getGroupId(), msg.getUserId());
        RunHandle handle = agentRunRegistry.beginOrSteer(sessionKey, extractPlainText(msg));
        if (handle == null) {
            // 已并入运行中的 agent（steering），不另起回复
            log.info("消息并入运行中的 agent steering：session={}", sessionKey);
            return;
        }

        List<ToolDefinition> tools = toolRegistry.toToolDefinitions();
        String callerUserId = msg.getUserId();
        String platform = msg.getBotType();
        String sessionId = "chat-" + msg.getMessageId();

        BbSendMessage envelope = new BbSendMessage(msg);
        MessageStreamSession session = bbMessageApi.startStream(envelope);

        // send_file 等工具的出站回传通道：把产物作为附件发回本会话（按 file 能力位降级）
        AgentReplySink replySink = new AgentReplySink() {
            @Override
            public boolean fileSupported() {
                String bt = msg.getBotType();
                if (BotType.BB.equals(bt)) {
                    // BB：按客户端握手上报的 file 能力位
                    return msg.getWebSocket() != null
                            && BbWebSocketServer.getCapabilities(msg.getWebSocket()).contains(BbCapability.FILE);
                }
                // Discord（JDA sendFiles）/ Telegram（sendDocument）原生支持任意文件附件。
                // QQ 官方机器人富媒体 file_type=4「文件」暂不开放、OneBot 未实现 → 不支持，
                // send_file 会返回 client_no_file_capability，AI 改用文字告知。
                return BotType.DISCORD.equals(bt) || BotType.TELEGRAM.equals(bt);
            }
            @Override
            public void sendFile(java.io.File file, String fileName) {
                BbSendMessage out = new BbSendMessage(msg);
                BbMessageContent content = BbMessageContent.buildLocalFileMessageContent(file);
                if (StringUtils.isNotBlank(fileName)) {
                    content.setFileName(fileName);
                }
                out.setMessageList(Collections.singletonList(content));
                bbMessageApi.sendMessage(out);
            }
            @Override
            public boolean imageSupported() {
                // 内联图片是 IM 基础内容类型，各平台 message API 均支持，无需能力位
                return true;
            }
            @Override
            public void sendImage(java.io.File image) {
                BbSendMessage out = new BbSendMessage(msg);
                out.setMessageList(Collections.singletonList(
                        BbMessageContent.buildLocalImageMessageContent(image)));
                bbMessageApi.sendMessage(out);
            }
        };

        try {
            toolLoopExecutor.run(
                    messages,
                    tools,
                    (toolName, argsJson) -> toolExecutor.invoke(toolName, argsJson, callerUserId, platform, msg.getGroupId(), sessionId, replySink),
                    maxSteps,
                    new StreamHandler() {
                        @Override
                        public void onTextDelta(String delta) {
                            session.appendDelta(delta);
                        }
                        @Override
                        public void onToolCalls(List<ToolCall> calls) {
                            // 仅日志，IM 端不展示工具调用细节
                            log.debug("AI 工具调用 step tool_calls: {}", calls);
                        }
                        @Override
                        public void onComplete(String fullText, String finishReason) {
                            session.complete();
                            if (StringUtils.isNoneBlank(fullText)) {
                                persistBotReply(msg, Collections.singletonList(
                                        BbMessageContent.buildTextContent(fullText)));
                            }
                        }
                        @Override
                        public void onError(Throwable err) {
                            log.error("AI 工具循环流式失败", err);
                            session.fail(err);
                        }
                    },
                    handle);
        } finally {
            List<String> leftover = handle.close();
            agentRunRegistry.end(sessionKey, handle);
            if (!leftover.isEmpty()) {
                // run 收尾瞬间漏接的 steering 消息：合并成一条新消息补派
                log.info("agent run 收尾残留 {} 条 steering 消息，补派", leftover.size());
                aiChatHandle(cloneWithText(msg, String.join("\n", leftover)));
            }
        }
    }

    /** 抽出消息里的纯文本（剥离老 agent 前缀），用作 steering 消息体。 */
    private String extractPlainText(BbReceiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : stripLegacyAgentPrefix(msg.getMessageContentList())) {
            if (BbSendMessageType.TEXT.equals(c.getType()) && c.getData() != null) {
                sb.append(c.getData());
            }
        }
        return sb.toString().trim();
    }

    /** 克隆一条接收消息、替换为指定文本，用于补派漏接的 steering 消息。 */
    private BbReceiveMessage cloneWithText(BbReceiveMessage src, String text) {
        BbReceiveMessage c = new BbReceiveMessage();
        c.setBotType(src.getBotType());
        c.setMessageType(src.getMessageType());
        c.setUserId(src.getUserId());
        c.setSender(src.getSender());
        c.setGroupId(src.getGroupId());
        c.setMessageId(src.getMessageId() + "-steer-" + System.nanoTime());
        c.setMessage(text);
        c.setMessageContentList(new ArrayList<>(
                Collections.singletonList(BbMessageContent.buildTextContent(text))));
        c.setAtUserList(src.getAtUserList());
        c.setSendTime(java.time.LocalDateTime.now());
        c.setWebSocket(src.getWebSocket());
        c.setConfig(src.getConfig());
        return c;
    }

    /** 阻塞分支：走 AiChatService（轻/重模型由 tier 决定）。 */
    private void blockingAiReply(BbReceiveMessage msg, List<ChatMessage> messages, ModelTier tier) {
        String answer;
        try {
            answer = aiChatService.chat(messages, tier);
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

    /** 流式分支：走 AiChatService.chatStream（轻/重模型由 tier 决定）。 */
    private void streamAiReply(BbReceiveMessage msg, List<ChatMessage> messages, ModelTier tier) {
        BbSendMessage envelope = new BbSendMessage(msg);
        MessageStreamSession session = bbMessageApi.startStream(envelope);
        aiChatService.chatStream(messages, new StreamHandler() {
            @Override
            public void onTextDelta(String delta) {
                session.appendDelta(delta);
            }
            @Override
            public void onToolCalls(java.util.List<ToolCall> calls) {
                // 聊天人格不开 function calling，正常不会触发
            }
            @Override
            public void onComplete(String fullText, String finishReason) {
                session.complete();
                if (StringUtils.isNoneBlank(fullText)) {
                    persistBotReply(msg, Collections.singletonList(BbMessageContent.buildTextContent(fullText)));
                }
            }
            @Override
            public void onError(Throwable err) {
                log.error("ai 流式回复异常", err);
                session.fail(err);
            }
        }, tier);
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

    /** 测试兼容用 2 参重载：等价于不挂工具。 */
    String composePersonality(String userId, List<String> clues) {
        return composePersonality(userId, clues, false);
    }

    /**
     * M8.6：personality = prompts.yml 模板 + clue suffix + 长期记忆 memory.md 注入。
     * useTools 时再追加工具使用引导 + 技能目录（progressive disclosure）。
     */
    String composePersonality(String userId, List<String> clues, boolean useTools) {
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
        if (useTools) {
            base = base + "\n\n" + toolGuidance;
            try {
                base = base + skillRegistry.describeAllForSystemPrompt();
            } catch (Exception e) {
                log.warn("注入技能目录失败 user={}", userId, e);
            }
        }
        // 任务边界：历史仅作上下文参考，只回应最新一条消息，避免重复处理之前已经说过/已经处理过的请求
        base = base + "\n\n" + HISTORY_BOUNDARY_GUIDANCE;
        return base;
    }

    /** 防止模型把历史里更早的消息当成待办再处理一遍。 */
    private static final String HISTORY_BOUNDARY_GUIDANCE =
            "【对话边界】上面的对话历史只用于帮你理解上下文。你只需要回应用户【最新】发来的这一条消息；" +
            "更早的消息仅作参考，不要重复回答、也不要重复执行历史里已经出现过或已经处理过的请求与指令。";

    /**
     * 向后兼容：剥离消息首段文本里的老 {@code agent } 前缀。返回新列表，不改原始消息体。
     */
    private List<BbMessageContent> stripLegacyAgentPrefix(List<BbMessageContent> contentList) {
        if (CollectionUtils.isEmpty(contentList)) {
            return contentList;
        }
        BbMessageContent first = contentList.get(0);
        if (!BbSendMessageType.TEXT.equals(first.getType()) || first.getData() == null) {
            return contentList;
        }
        String text = first.getData().toString();
        for (String prefix : LEGACY_AGENT_PREFIXES) {
            if (text.startsWith(prefix)) {
                List<BbMessageContent> copy = new ArrayList<>(contentList);
                copy.set(0, BbMessageContent.buildTextContent(text.substring(prefix.length()).trim()));
                return copy;
            }
        }
        return contentList;
    }

    // =========================================================================
    // 决策 / 路由
    // =========================================================================

    /**
     * 判断本轮消息是否需要 AI 回复。私聊必回；群聊@必回；群聊未@时按配置 + 关键词 + 概率门控。
     * 私聊与 @机器人 标记为 directTrigger（会挂工具）；概率自动回复不挂工具。
     * 包级可见：方便单元测试在 Mock 服务后直接调用。
     */
    ReplyDecision decideShouldReply(BbReceiveMessage msg) {
        if (MessageType.PRIVATE.equals(msg.getMessageType())) {
            return ReplyDecision.replyDirect(Collections.emptyList());
        }
        if (!isGroupLike(msg)) {
            return ReplyDecision.skip();
        }

        boolean atMe = msg.getAtUserList() != null && msg.getAtUserList().stream()
                .filter(MessageUser::getBotFlag).findFirst().isPresent();
        if (atMe) {
            return ReplyDecision.replyDirect(aiClueService.selectClue(formatForClueLookup(msg)));
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
