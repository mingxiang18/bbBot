package com.bb.bot.common.util.aiChat.provider;

import java.util.List;

/**
 * AI 模型调用抽象（OpenAI Chat Completion 协议）。每次调用传入 {@link ModelSpec}
 * 指定用哪个模型（baseUrl / apiKey / model / kind / vision），由上层 {@code AiChatService}
 * 按角色解析。
 *
 * @author ren
 */
public interface AIProvider {

    /**
     * 同步聊天调用（不带 function calling）。
     *
     * @param spec     本次调用使用的模型
     * @param messages 已构建好的会话消息
     * @return 模型回复文本
     */
    String chat(ModelSpec spec, List<ChatMessage> messages) throws AIException;

    /**
     * 流式聊天调用（支持 function calling，阻塞至 onComplete / onError）。
     *
     * @param spec     本次调用使用的模型
     * @param messages 上下文消息
     * @param tools    可调用工具列表，{@code null}/空表示本轮不开 function calling
     * @param handler  回调处理器
     */
    void chatStream(ModelSpec spec,
                    List<ChatMessage> messages,
                    List<ToolDefinition> tools,
                    StreamHandler handler) throws AIException;
}
