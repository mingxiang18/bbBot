package com.bb.bot.common.util.aiChat.provider;

import java.util.List;

/**
 * AI 模型调用抽象。一个实现对应一家提供商（OpenAI / DeepSeek / …）。
 *
 * <p>同时提供两种调用方式：</p>
 * <ul>
 *   <li>{@link #chat(List)} 阻塞返回完整文本（不支持 function calling）</li>
 *   <li>{@link #chatStream(List, List, StreamHandler)} SSE 流式，支持 tool_calls
 *       delta 聚合 → {@link StreamHandler#onToolCalls(List)} 回调；多轮工具循环由
 *       上层 {@code ToolLoopExecutor} 编排</li>
 * </ul>
 *
 * @author ren
 */
public interface AIProvider {

    /**
     * 同步聊天调用（不带 function calling）。
     *
     * @param messages 已经构建好的会话消息（含 system / user / assistant）
     * @return 模型回复文本
     * @throws AIException 鉴权、限流、永久失败等情况
     */
    String chat(List<ChatMessage> messages) throws AIException;

    /**
     * 流式聊天调用（支持 function calling）。
     *
     * <p>provider 内部走 SSE，按事件类型回调 handler；tool_calls delta 在流结束
     * 时已经按 index 聚合完毕，通过 {@link StreamHandler#onToolCalls(List)} 一次
     * 性交付，调用方不需要自己拼参数 JSON。</p>
     *
     * <p>本方法**阻塞当前线程**直到流自然结束或出错（onComplete / onError 二选一）。
     * 多轮工具循环由上层 {@code ToolLoopExecutor} 通过反复调用本方法实现。</p>
     *
     * @param messages 上下文消息列表（含 system / user / assistant + tool）
     * @param tools    可调用工具列表，{@code null} 或空表示本轮不开 function calling
     * @param handler  回调处理器
     * @throws AIException 鉴权、限流、永久失败等情况（瞬时网络错误已重试过）
     */
    void chatStream(List<ChatMessage> messages,
                    List<ToolDefinition> tools,
                    StreamHandler handler) throws AIException;

    /** 是否完成必要配置（如 apiKey）。 */
    boolean isConfigured();

    /** 唯一名称，与 {@code ai.active-provider} 配置项匹配。 */
    String name();

    /** 该 provider 当前配置的模型是否支持视觉（图片输入）。默认 false。 */
    default boolean visionEnable() {
        return false;
    }
}
