package com.bb.bot.common.util.aiChat.provider;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 业务侧入口：按角色（{@link ModelTier}）解析出 {@link ModelSpec} 并调用唯一的
 * {@link OpenAiCompatProvider}。角色 → 模型名的映射来自 {@code ai.roles}，
 * 模型参数来自 {@code ai.models}。
 *
 * <p>LIGHT / VISION 未配置时回退 HEAVY（CHAT）。视觉桥接在委托前对消息处理一次。</p>
 *
 * @author ren
 */
@Slf4j
@Service
public class AiChatService {

    private final AIProvider provider;
    private final AIProviderProperties properties;

    @Autowired
    @Lazy
    private VisionBridge visionBridge;

    @Autowired
    public AiChatService(AIProvider provider, AIProviderProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @PostConstruct
    public void logStartup() {
        // 回填 spec.name，便于日志
        properties.getModels().forEach((k, v) -> { if (v != null) v.setName(k); });
        AIProviderProperties.Roles r = properties.getRoles();
        log.info("AI models: {}, roles heavy={} light={} vision={}, configured={}",
                properties.getModels().keySet(), r.getHeavy(), r.getLight(), r.getVision(), isConfigured());
    }

    /** heavy（主）模型是否就绪。 */
    public boolean isConfigured() {
        ModelSpec s = specForTier(ModelTier.CHAT);
        return s != null && s.isConfigured();
    }

    /** 是否配置了可用的视觉模型。 */
    public boolean visionConfigured() {
        ModelSpec s = rawSpec(properties.getRoles().getVision());
        return s != null && s.isConfigured() && s.isVision();
    }

    /** 解析某角色对应的模型；LIGHT/VISION 未配置回退 HEAVY。null 表示 heavy 都没配。 */
    public ModelSpec specForTier(ModelTier tier) {
        String name = switch (tier) {
            case LIGHT -> firstNonBlank(properties.getRoles().getLight(), properties.getRoles().getHeavy());
            case VISION -> firstNonBlank(properties.getRoles().getVision(), properties.getRoles().getHeavy());
            default -> properties.getRoles().getHeavy();
        };
        ModelSpec s = rawSpec(name);
        if (s == null && tier != ModelTier.CHAT) {
            s = rawSpec(properties.getRoles().getHeavy());
        }
        return s;
    }

    private ModelSpec rawSpec(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        ModelSpec s = properties.getModels().get(name);
        if (s != null && s.getName() == null) {
            s.setName(name);
        }
        return s;
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.isNotBlank(a) ? a : b;
    }

    // ---- chat（阻塞） ----

    public String chat(List<ChatMessage> messages) {
        return chat(messages, ModelTier.CHAT);
    }

    public String chat(List<ChatMessage> messages, ModelTier tier) {
        ModelSpec spec = specForTier(tier);
        if (spec == null || !spec.isConfigured()) {
            log.warn("无可用模型（tier={}），跳过", tier);
            return null;
        }
        List<ChatMessage> bridged = visionBridge.bridgeIfNeeded(messages, spec);
        try {
            AiCallContext.setModelRole(tier.name());
            return provider.chat(spec, bridged);
        } finally {
            AiCallContext.clearModelRole();
        }
    }

    // ---- chatStream（流式） ----

    public void chatStream(List<ChatMessage> messages, StreamHandler handler) {
        chatStream(messages, handler, ModelTier.CHAT);
    }

    public void chatStream(List<ChatMessage> messages, StreamHandler handler, ModelTier tier) {
        ModelSpec spec = specForTier(tier);
        if (spec == null || !spec.isConfigured()) {
            handler.onError(new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "no configured AI model for tier " + tier));
            return;
        }
        List<ChatMessage> bridged = visionBridge.bridgeIfNeeded(messages, spec);
        try {
            AiCallContext.setModelRole(tier.name());
            provider.chatStream(spec, bridged, null, handler);
        } finally {
            AiCallContext.clearModelRole();
        }
    }

    /** 给 ToolLoopExecutor 用：底层 provider 与 heavy spec。 */
    public AIProvider provider() {
        return provider;
    }

    public ModelSpec heavySpec() {
        return specForTier(ModelTier.CHAT);
    }
}
