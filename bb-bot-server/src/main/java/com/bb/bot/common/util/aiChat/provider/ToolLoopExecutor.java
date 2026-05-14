package com.bb.bot.common.util.aiChat.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Function-calling 循环编排器：跑「调 LLM → 拿 tool_calls → 执行工具 → 把结果
 * 回灌 → 再调 LLM」直到 finish_reason=stop 或达到 maxSteps。
 *
 * <p>跟 provider 解耦：本类只负责循环 + tool 结果回灌，每步流式调用都委托给当前
 * 激活的 {@link AIProvider#chatStream}。换 LLM provider 不影响循环行为。</p>
 *
 * <p>每步内部的 token 流式输出通过外层 {@code outputHandler.onTextDelta} 实时
 * 转给调用方（一般是 IM 适配器的 streamMessage 会话）。工具调用阶段 LLM 通常
 * 不吐文本（finish_reason=tool_calls），用户看到的"流式"是各步的文本拼起来。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class ToolLoopExecutor {

    @Autowired
    private AiChatService aiChatService;

    /**
     * 跑工具循环。
     *
     * @param initialMessages 起始消息（system + user，会原地被改：append assistant / tool）
     * @param tools           可调用的工具列表
     * @param toolInvoker     工具执行器：BiFunction&lt;toolName, argsJson&gt; → 结果字符串
     * @param maxSteps        循环上限
     * @param outputHandler   外层流式输出：onTextDelta / onComplete / onError 被透传
     */
    public void run(List<ChatMessage> initialMessages,
                    List<ToolDefinition> tools,
                    BiFunction<String, String, String> toolInvoker,
                    int maxSteps,
                    StreamHandler outputHandler) {

        AIProvider provider = aiChatService.current();
        if (provider == null || !provider.isConfigured()) {
            outputHandler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "no configured AI provider"));
            return;
        }

        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        StringBuilder accumulatedText = new StringBuilder();

        for (int step = 1; step <= maxSteps; step++) {
            StepCollector step1 = new StepCollector(outputHandler, accumulatedText);
            try {
                provider.chatStream(messages, tools, step1);
            } catch (AIException e) {
                // chatStream 内部已经调 outputHandler.onError，这里只补 log
                log.warn("ToolLoopExecutor step {} provider error: {}", step, e.getMessage());
                return;
            }

            String finish = step1.finishReason;
            if ("stop".equals(finish) || finish == null) {
                outputHandler.onComplete(accumulatedText.toString(), "stop");
                return;
            }
            if (!"tool_calls".equals(finish) || step1.toolCalls.isEmpty()) {
                // length / content_filter 等：当 stop 处理
                outputHandler.onComplete(accumulatedText.toString(), finish);
                return;
            }

            // 把 assistant 的工具调用决策追加到上下文
            messages.add(ChatMessage.assistantWithToolCalls(
                    step1.assistantText.length() == 0 ? null : step1.assistantText.toString(),
                    new ArrayList<>(step1.toolCalls)));

            // 串行执行每个工具，把结果作为 tool 消息回灌
            for (ToolCall call : step1.toolCalls) {
                String result;
                try {
                    result = toolInvoker.apply(call.getName(), call.getArgumentsJson());
                } catch (Exception toolErr) {
                    log.warn("tool {} 调用器异常", call.getName(), toolErr);
                    result = "{\"error\":\"executor_threw\",\"message\":\""
                            + escape(toolErr.getMessage()) + "\"}";
                }
                messages.add(ChatMessage.tool(call.getId(), result));
            }
        }

        log.warn("ToolLoopExecutor 达到 maxSteps={}，强制收尾", maxSteps);
        accumulatedText.append("\n[已达工具调用步数上限，停止]");
        outputHandler.onComplete(accumulatedText.toString(), "max_steps");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 收集本步 SSE 流到的：完整文本（本步） / finish_reason / 聚合后的 tool_calls。
     * 同时把 onTextDelta 透传给外层（一边累计一边吐字给 IM 端）。
     */
    private static class StepCollector implements StreamHandler {
        private final StreamHandler outer;
        private final StringBuilder accumulatedText;
        private final StringBuilder assistantText = new StringBuilder();
        private List<ToolCall> toolCalls = new ArrayList<>();
        private String finishReason;

        StepCollector(StreamHandler outer, StringBuilder accumulatedText) {
            this.outer = outer;
            this.accumulatedText = accumulatedText;
        }

        @Override
        public void onTextDelta(String delta) {
            if (delta == null || delta.isEmpty()) return;
            assistantText.append(delta);
            accumulatedText.append(delta);
            outer.onTextDelta(delta);
        }

        @Override
        public void onToolCalls(List<ToolCall> calls) {
            this.toolCalls = calls;
        }

        @Override
        public void onComplete(String fullText, String finishReason) {
            this.finishReason = finishReason;
        }

        @Override
        public void onError(Throwable err) {
            outer.onError(err);
        }
    }
}
