package com.bb.bot.common.util.aiChat.provider;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务侧入口：选出目标 {@link AIProvider}（按层级 {@link ModelTier}）并代理 chat / 配置探测。
 * Handler 注入这个服务，而不是某个具体的 provider。
 *
 * <p>多模型路由：层级 → (provider, model)。CHAT 永远 = activeProvider + 其 model；
 * LIGHT / VISION 可由 {@code ai.tiers.*} 覆写。per-call model 通过 {@link AiCallContext}
 * 的 modelOverride 传到 provider 层。</p>
 *
 * @author ren
 */
@Slf4j
@Service
public class AiChatService {

    private final Map<String, AIProvider> providers = new HashMap<>();
    private final AIProviderProperties properties;

    /** 视觉桥接：主模型无视觉时把图片转文字描述。与本类循环依赖，用 @Lazy 打破。 */
    @Autowired
    @Lazy
    private VisionBridge visionBridge;

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
     * 调用激活 provider 进行聊天（CHAT 层级）。
     */
    public String chat(List<ChatMessage> messages) {
        return chat(messages, ModelTier.CHAT);
    }

    /**
     * 按层级调用聊天。调用方传入空消息或未配置 provider 时返回 null（沿用旧 API 的"未配置即静默"语义，
     * 但失败抛 {@link AIException} 让调用方决定回退）。
     */
    public String chat(List<ChatMessage> messages, ModelTier tier) {
        AIProvider p = resolveProvider(tier);
        if (p == null) {
            log.warn("No AI provider matches tier [{}] (active={})", tier, properties.getActiveProvider());
            return null;
        }
        if (!p.isConfigured()) {
            return null;
        }
        List<ChatMessage> bridged = visionBridge.bridgeIfNeeded(messages, p);
        String model = resolveModelForTier(tier);
        try {
            applyTier(model, tier);
            return p.chat(bridged);
        } finally {
            clearTier();
        }
    }

    /**
     * 流式调用（无 function calling，CHAT 层级）。
     */
    public void chatStream(List<ChatMessage> messages, StreamHandler handler) {
        chatStream(messages, handler, ModelTier.CHAT);
    }

    /**
     * 按层级流式调用。上层 BbAiChatHandler 用这个，IM 端通过 handler.onTextDelta 实时吐字。
     */
    public void chatStream(List<ChatMessage> messages, StreamHandler handler, ModelTier tier) {
        AIProvider p = resolveProvider(tier);
        if (p == null) {
            handler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "no AI provider matches tier [" + tier + "]"));
            return;
        }
        if (!p.isConfigured()) {
            handler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "AI provider [" + p.name() + "] not configured"));
            return;
        }
        List<ChatMessage> bridged = visionBridge.bridgeIfNeeded(messages, p);
        String model = resolveModelForTier(tier);
        try {
            applyTier(model, tier);
            p.chatStream(bridged, null, handler);
        } finally {
            clearTier();
        }
    }

    /** 当前激活 provider（CHAT 层级 = activeProvider）。 */
    public AIProvider current() {
        return providers.get(properties.getActiveProvider());
    }

    /** 解析某层级对应的 provider。LIGHT / VISION 可指向其它 provider；否则回退 activeProvider。 */
    public AIProvider resolveProvider(ModelTier tier) {
        AIProviderProperties.TierTarget target = targetOf(tier);
        if (target != null && StringUtils.isNotBlank(target.getProvider())) {
            AIProvider p = providers.get(target.getProvider());
            if (p != null) {
                return p;
            }
            log.warn("tier [{}] 指定的 provider [{}] 不存在，回退 active", tier, target.getProvider());
        }
        return current();
    }

    /** 解析某层级对应的 model 覆写值；无覆写返回 null（provider 用自身配置 model）。 */
    public String resolveModelForTier(ModelTier tier) {
        AIProviderProperties.TierTarget target = targetOf(tier);
        if (target != null && StringUtils.isNotBlank(target.getModel())) {
            return target.getModel();
        }
        return null;
    }

    private AIProviderProperties.TierTarget targetOf(ModelTier tier) {
        switch (tier) {
            case LIGHT:
                return properties.getTiers().getLight();
            case VISION:
                return properties.getTiers().getVision();
            default:
                return null; // CHAT 不可覆写
        }
    }

    private void applyTier(String model, ModelTier tier) {
        if (StringUtils.isNotBlank(model)) {
            AiCallContext.setModelOverride(model);
        }
        AiCallContext.setModelRole(tier.name());
    }

    private void clearTier() {
        AiCallContext.clearModelOverride();
        AiCallContext.clearModelRole();
    }
}
