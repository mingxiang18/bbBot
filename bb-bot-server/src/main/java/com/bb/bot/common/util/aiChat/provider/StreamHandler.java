package com.bb.bot.common.util.aiChat.provider;

import java.util.List;

/**
 * 流式 LLM 调用的回调接口。
 *
 * <p>provider 的 {@code chatStream} 会按到达顺序回调本 handler：</p>
 * <ol>
 *   <li>每个文本 token delta → {@link #onTextDelta(String)}</li>
 *   <li>本轮模型决定调工具（function calling）→ 流式 delta 聚合完后
 *       {@link #onToolCalls(List)} 一次性通知（不是每个 delta 都回调，避免
 *       调用方自己拼参数 JSON）</li>
 *   <li>流自然结束 → {@link #onComplete(String, String)}，传完整文本 +
 *       finish_reason（"stop" / "tool_calls" / "length" 等）</li>
 *   <li>异常 → {@link #onError(Throwable)}</li>
 * </ol>
 *
 * <p>onComplete 和 onError 互斥，只调一个。</p>
 *
 * @author ren
 */
public interface StreamHandler {

    /** 模型吐字的增量（可能频繁触发）。 */
    void onTextDelta(String delta);

    /**
     * thinking 模式模型的思维链增量（reasoning_content）。普通模型不会触发。
     * 默认空实现：只有 ToolLoopExecutor 关心它（用于回灌时带回 assistant
     * 消息），IM 端不展示思维链。
     */
    default void onReasoningDelta(String delta) {}

    /**
     * 本轮模型决定的工具调用列表（function calling）。一轮 SSE 流只调一次，
     * 已经把所有 tool_calls delta 按 index 聚合好。
     */
    default void onToolCalls(List<ToolCall> toolCalls) {}

    /**
     * 流自然结束（finish_reason 收到）。
     *
     * @param fullText      本轮累计文本（可能为空字符串，纯工具调用时模型不一定吐文本）
     * @param finishReason  OpenAI 协议的 finish_reason: "stop" / "tool_calls" /
     *                      "length" / "content_filter" 等
     */
    void onComplete(String fullText, String finishReason);

    /** 网络 / 解析 / 鉴权等异常。已收到的内容会先通过 onComplete 交付，再调 onError。 */
    void onError(Throwable error);
}
