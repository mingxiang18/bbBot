package com.bb.bot.common.util.aiChat.provider;

import java.util.List;

/**
 * AI 模型调用抽象。一个实现对应一家提供商（OpenAI / DeepSeek / …）。
 *
 * @author ren
 */
public interface AIProvider {

    /**
     * 同步聊天调用。
     *
     * @param messages 已经构建好的会话消息（含 system / user / assistant）
     * @return 模型回复文本
     * @throws AIException 鉴权、限流、永久失败等情况
     */
    String chat(List<ChatMessage> messages) throws AIException;

    /** 是否完成必要配置（如 apiKey）。 */
    boolean isConfigured();

    /** 唯一名称，与 {@code ai.active-provider} 配置项匹配。 */
    String name();
}
