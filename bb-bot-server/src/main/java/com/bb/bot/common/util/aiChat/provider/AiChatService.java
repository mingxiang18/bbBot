package com.bb.bot.common.util.aiChat.provider;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务侧入口：选出当前激活的 {@link AIProvider} 并代理 chat / 配置探测。
 * Handler 注入这个服务，而不是某个具体的 provider。
 *
 * @author ren
 */
@Slf4j
@Service
public class AiChatService {

    private final Map<String, AIProvider> providers = new HashMap<>();
    private final AIProviderProperties properties;

    @Autowired
    public AiChatService(List<AIProvider> providerBeans, AIProviderProperties properties) {
        for (AIProvider p : providerBeans) {
            providers.put(p.name(), p);
        }
        this.properties = properties;
    }

    @PostConstruct
    public void logStartup() {
        log.info("AI providers registered: {}, active: {}, configured: {}",
                providers.keySet(), properties.getActiveProvider(), isConfigured());
    }

    /** 当前激活的 provider 是否完成必要配置。 */
    public boolean isConfigured() {
        AIProvider p = current();
        return p != null && p.isConfigured();
    }

    /**
     * 调用激活 provider 进行聊天。
     * 调用方传入空消息或未配置 provider 时返回 null（沿用旧 API 的"未配置即静默"语义，
     * 但失败抛 {@link AIException} 让调用方决定回退）。
     */
    public String chat(List<ChatMessage> messages) {
        AIProvider p = current();
        if (p == null) {
            log.warn("No AI provider matches active name [{}]", properties.getActiveProvider());
            return null;
        }
        if (!p.isConfigured()) {
            return null;
        }
        return p.chat(messages);
    }

    /**
     * 流式调用（无 function calling）。上层 BbAiChatHandler 用这个，
     * IM 端通过 handler.onTextDelta 实时吐字。
     */
    public void chatStream(List<ChatMessage> messages, StreamHandler handler) {
        AIProvider p = current();
        if (p == null) {
            handler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "no AI provider matches active name [" + properties.getActiveProvider() + "]"));
            return;
        }
        if (!p.isConfigured()) {
            handler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "AI provider [" + p.name() + "] not configured"));
            return;
        }
        p.chatStream(messages, null, handler);
    }

    public AIProvider current() {
        return providers.get(properties.getActiveProvider());
    }
}
