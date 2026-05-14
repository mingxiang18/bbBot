package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.core.AiToolExecutor;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.aiAgent.skills.SkillRegistry;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.api.MessageStreamSession;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.StreamHandler;
import com.bb.bot.common.util.aiChat.provider.ToolCall;
import com.bb.bot.common.util.aiChat.provider.ToolDefinition;
import com.bb.bot.common.util.aiChat.provider.ToolLoopExecutor;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent 路由：让用户在聊天平台远程派活给 AI。
 *
 * <p>触发方式：群里 @bot 或私聊，消息以 {@code agent } 开头（注意空格）。
 * 例：{@code @bbBot agent 抓一下 example.com 的标题}。</p>
 *
 * <p>与 {@link com.bb.bot.handler.aiChat.BbAiChatHandler} 的关系：
 * 两者均处于 BB 协议层，但 chat 走默认人格 + 普通流式；agent 走 system prompt
 * 鼓励工具调用 + function calling 循环。</p>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI Agent")
public class BbAiAgentHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ToolLoopExecutor toolLoopExecutor;

    @Autowired
    private AiToolRegistry toolRegistry;

    @Autowired
    private AiToolExecutor toolExecutor;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private com.bb.bot.aiAgent.memory.MemoryCompiler memoryCompiler;

    @Value("${aiAgent.maxSteps:10}")
    private int maxSteps;

    @Value("${aiAgent.systemPrompt:你是 bbBot 的远程 Agent 模式。" +
            "用户会通过自然语言派活给你，你应优先调用已注册的工具完成任务，" +
            "工具结果不足时再请求用户补充信息。" +
            "回复中用简体中文，简洁、信息密集、避免无意义客套。}")
    private String systemPrompt;

    @Rule(
            eventType = EventType.MESSAGE,
            needAtMe = true,
            ruleType = RuleType.REGEX,
            keyword = {"^/?[Aa]gent\\s"},
            name = "AI Agent 派活"
    )
    public void agentHandle(BbReceiveMessage bbReceiveMessage) {
        String prompt = extractPrompt(bbReceiveMessage);
        if (StringUtils.isBlank(prompt)) {
            replyText(bbReceiveMessage, "用法：agent <自然语言指令>");
            return;
        }

        log.info("AI Agent 派活 user={} prompt={}", bbReceiveMessage.getUserId(), prompt);

        // M8.6：把 caller user 的长期记忆（memory.md）prepend 到 system prompt。
        // 跨进程持久 + progressive disclosure：LLM 一开始就看得到 facts/today/week/longterm。
        String userMemoryMd = "";
        try {
            userMemoryMd = memoryCompiler.ensureCompiledMemory(bbReceiveMessage.getUserId());
        } catch (Exception e) {
            log.warn("ensureCompiledMemory 失败 user={}", bbReceiveMessage.getUserId(), e);
        }
        String memoryBlock = "";
        if (org.apache.commons.lang3.StringUtils.isNoneBlank(userMemoryMd)) {
            memoryBlock = "\n\n--- 你的长期记忆（来自历史会话的提炼，请用作上下文）---\n"
                    + userMemoryMd + "\n--- 长期记忆结束 ---\n";
        }
        // 把 SKILL 目录（progressive disclosure 的 metadata 层）拼进 system prompt
        String effectiveSystemPrompt = systemPrompt + memoryBlock + skillRegistry.describeAllForSystemPrompt();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(effectiveSystemPrompt));
        messages.add(ChatMessage.user(prompt));

        List<ToolDefinition> tools = toolRegistry.toToolDefinitions();
        String callerUserId = bbReceiveMessage.getUserId();
        String platform = bbReceiveMessage.getBotType();
        String sessionId = "agent-" + System.currentTimeMillis() + "-" + Integer.toHexString(System.identityHashCode(bbReceiveMessage));

        BbSendMessage envelope = new BbSendMessage(bbReceiveMessage);
        MessageStreamSession session = bbMessageApi.startStream(envelope);

        toolLoopExecutor.run(
                messages,
                tools,
                (toolName, argsJson) -> toolExecutor.invoke(toolName, argsJson, callerUserId, platform, sessionId),
                maxSteps,
                new StreamHandler() {
                    @Override
                    public void onTextDelta(String delta) {
                        session.appendDelta(delta);
                    }
                    @Override
                    public void onToolCalls(java.util.List<ToolCall> calls) {
                        // 单纯日志，IM 侧不显示工具调用细节（避免吐工具 JSON 出来）
                        log.debug("agent step tool_calls: {}", calls);
                    }
                    @Override
                    public void onComplete(String fullText, String finishReason) {
                        session.complete();
                    }
                    @Override
                    public void onError(Throwable err) {
                        log.error("AI Agent 流式失败", err);
                        session.fail(err);
                    }
                }
        );
    }

    /**
     * 把消息原文里 {@code agent xxxx} 后面的部分提取出来。
     * BbReceiveMessage 已经在前置阶段把 @ 部分剥离，剩下的就是命令体。
     */
    private String extractPrompt(BbReceiveMessage bbReceiveMessage) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : bbReceiveMessage.getMessageContentList()) {
            if (BbSendMessageType.TEXT.equals(c.getType()) && c.getData() != null) {
                sb.append(c.getData());
            }
        }
        String full = sb.toString().trim();
        for (String prefix : new String[]{"agent ", "/agent ", "Agent "}) {
            if (full.startsWith(prefix)) {
                return full.substring(prefix.length()).trim();
            }
        }
        return full;
    }

    private void replyText(BbReceiveMessage bbReceiveMessage, String text) {
        BbSendMessage send = new BbSendMessage(bbReceiveMessage);
        send.setMessageList(java.util.Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(send);
    }
}
