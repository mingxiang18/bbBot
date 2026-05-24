package com.bb.bot.common.util.aiChat.provider;

import com.bb.bot.aiAgent.core.RunHandle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Function-calling 循环编排器：跑「调 LLM → 拿 tool_calls → 执行工具 → 把结果
 * 回灌 → 再调 LLM」直到 finish_reason=stop 或达到 maxSteps。
 *
 * <p>跟 provider 解耦：本类只负责循环 + tool 结果回灌，每步流式调用都委托给当前
 * 激活的 {@link AIProvider#chatStream}。换 LLM provider 不影响循环行为。</p>
 *
 * <p>三项运行时增强（借鉴 earendil-works/pi 的 agent runtime）：</p>
 * <ul>
 *   <li><b>工具并行执行</b>：一轮里多个无依赖的 tool_call 并发跑，结果按原序回灌</li>
 *   <li><b>会话内上下文压缩</b>：每步调 LLM 前经 {@link ContextCompactor} 裁剪超长上下文</li>
 *   <li><b>steering</b>：传入 {@link RunHandle} 后，用户运行期间追加的消息会在步与步
 *       之间并入对话，可中途改方向；模型已想收尾时若有新消息也会继续</li>
 * </ul>
 *
 * @author ren
 */
@Slf4j
@Component
public class ToolLoopExecutor {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private ContextCompactor contextCompactor;

    @Autowired
    private VisionBridge visionBridge;

    /** 是否并行执行同一轮的多个工具调用。 */
    @Value("${aiAgent.toolParallel:true}")
    private boolean toolParallel;

    /** 并行工具执行的等待上限（AiToolExecutor 内部单工具超时 30s，这里留余量）。 */
    private static final long TOOL_FANOUT_TIMEOUT_SECONDS = 45;

    /** 工具并发执行用的有界线程池。 */
    private final ExecutorService toolFanoutPool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "ai-tool-fanout-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    /**
     * 跑工具循环（无 steering，cron 等非交互场景用）。
     */
    public void run(List<ChatMessage> initialMessages,
                    List<ToolDefinition> tools,
                    BiFunction<String, String, String> toolInvoker,
                    int maxSteps,
                    StreamHandler outputHandler) {
        run(initialMessages, tools, toolInvoker, maxSteps, outputHandler, null);
    }

    /**
     * 跑工具循环。
     *
     * @param initialMessages 起始消息（system + user，会被复制后在副本上增删）
     * @param tools           可调用的工具列表
     * @param toolInvoker     工具执行器：BiFunction&lt;toolName, argsJson&gt; → 结果字符串
     * @param maxSteps        循环上限
     * @param outputHandler   外层流式输出：onTextDelta / onComplete / onError 被透传
     * @param handle          steering 句柄；为 null 则关闭 steering
     */
    public void run(List<ChatMessage> initialMessages,
                    List<ToolDefinition> tools,
                    BiFunction<String, String, String> toolInvoker,
                    int maxSteps,
                    StreamHandler outputHandler,
                    RunHandle handle) {

        AIProvider provider = aiChatService.provider();
        ModelSpec spec = aiChatService.heavySpec();
        if (spec == null || !spec.isConfigured()) {
            outputHandler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "no configured AI model (role heavy)"));
            return;
        }

        // 主模型无视觉但消息带图时，先用视觉模型把图片转成文字描述注入上下文（描述带缓存）
        List<ChatMessage> messages = new ArrayList<>(visionBridge.bridgeIfNeeded(initialMessages, spec));
        StringBuilder accumulatedText = new StringBuilder();

        for (int step = 1; step <= maxSteps; step++) {
            // 1. steering：把运行期间排队的用户消息并入上下文
            if (handle != null) {
                appendSteering(messages, handle.drain());
            }
            // 2. 会话内上下文压缩（超长才会真正裁剪）
            messages = contextCompactor.compactIfNeeded(messages);

            // 3. 调 LLM
            StepCollector collector = new StepCollector(outputHandler, accumulatedText);
            try {
                provider.chatStream(spec, messages, tools, collector);
            } catch (AIException e) {
                // chatStream 内部已调 outputHandler.onError，这里只补 log
                log.warn("ToolLoopExecutor step {} provider error: {}", step, e.getMessage());
                return;
            }

            String finish = collector.finishReason;
            boolean wantTools = "tool_calls".equals(finish) && !collector.toolCalls.isEmpty();
            if (!wantTools) {
                // 模型想收尾：停止前看有没有 steering 消息插队
                if (handle != null) {
                    List<String> steer = handle.drainOrClose();
                    if (!steer.isEmpty()) {
                        appendSteering(messages, steer);
                        continue;  // 不停，带着新消息再跑一轮
                    }
                }
                String reason = ("stop".equals(finish) || finish == null) ? "stop" : finish;
                outputHandler.onComplete(accumulatedText.toString(), reason);
                return;
            }

            // 把 assistant 的工具调用决策追加到上下文
            ChatMessage assistantMsg = ChatMessage.assistantWithToolCalls(
                    collector.assistantText.length() == 0 ? null : collector.assistantText.toString(),
                    new ArrayList<>(collector.toolCalls));
            // thinking 模式模型要求把本轮 reasoning_content 随 assistant 消息回灌
            if (collector.reasoningText.length() > 0) {
                assistantMsg.setReasoningContent(collector.reasoningText.toString());
            }
            messages.add(assistantMsg);

            // 执行工具（并行 / 串行），结果按原序作为 tool 消息回灌
            List<ToolCall> calls = collector.toolCalls;
            List<String> results = executeToolCalls(calls, toolInvoker);
            for (int i = 0; i < calls.size(); i++) {
                messages.add(ChatMessage.tool(calls.get(i).getId(), results.get(i)));
            }
        }

        log.warn("ToolLoopExecutor 达到 maxSteps={}，强制收尾", maxSteps);
        accumulatedText.append("\n[已达工具调用步数上限，停止]");
        outputHandler.onComplete(accumulatedText.toString(), "max_steps");
    }

    /** 把 steering 文本作为 user 消息并入上下文。 */
    private void appendSteering(List<ChatMessage> messages, List<String> steering) {
        for (String s : steering) {
            if (StringUtils.isNotBlank(s)) {
                messages.add(ChatMessage.user(s));
            }
        }
    }

    /**
     * 执行本轮的全部 tool_call。单个 / 关闭并行时串行执行；否则并发执行后按原序收集。
     */
    private List<String> executeToolCalls(List<ToolCall> calls,
                                          BiFunction<String, String, String> toolInvoker) {
        int n = calls.size();
        List<String> results = new ArrayList<>(n);
        if (n == 1 || !toolParallel) {
            for (ToolCall call : calls) {
                results.add(invokeOne(toolInvoker, call));
            }
            return results;
        }

        List<Future<String>> futures = new ArrayList<>(n);
        for (ToolCall call : calls) {
            futures.add(toolFanoutPool.submit(() -> invokeOne(toolInvoker, call)));
        }
        for (int i = 0; i < n; i++) {
            try {
                results.add(futures.get(i).get(TOOL_FANOUT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
                futures.get(i).cancel(true);
                log.warn("tool {} 并行执行超时 / 失败", calls.get(i).getName(), e);
                results.add("{\"error\":\"executor_threw\",\"message\":\"parallel_execution_failed\"}");
            }
        }
        return results;
    }

    /** 执行单个工具调用，调用器抛异常时兜底成错误结果回灌给 LLM。 */
    private String invokeOne(BiFunction<String, String, String> toolInvoker, ToolCall call) {
        try {
            return toolInvoker.apply(call.getName(), call.getArgumentsJson());
        } catch (Exception toolErr) {
            log.warn("tool {} 调用器异常", call.getName(), toolErr);
            return "{\"error\":\"executor_threw\",\"message\":\""
                    + escape(toolErr.getMessage()) + "\"}";
        }
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
        private final StringBuilder reasoningText = new StringBuilder();
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
        public void onReasoningDelta(String delta) {
            if (delta == null || delta.isEmpty()) return;
            // 思维链只累计、不透传给 IM 端（用户看 content，不看 reasoning）
            reasoningText.append(delta);
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
