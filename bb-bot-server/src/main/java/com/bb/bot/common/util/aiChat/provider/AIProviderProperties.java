package com.bb.bot.common.util.aiChat.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 调用全局配置：选哪个 provider、各 provider 的 baseUrl/key/model、重试参数。
 * 由 {@link com.bb.bot.config.LegacyAiConfigBackfill} 在启动期把旧的 chatGPT.* 配置回填到 openai 项。
 *
 * @author ren
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProviderProperties {

    /** 当前激活的 provider 名（与 {@link AIProvider#name()} 对应）。 */
    private String activeProvider = "openai";

    private ProviderConfig openai = new ProviderConfig();
    private ProviderConfig deepseek = new ProviderConfig();
    private RetryConfig retry = new RetryConfig();

    /** 模型层级路由：缺省即回退到 activeProvider + 其 model（CHAT 永远如此，不在此配置）。 */
    private TierRouting tiers = new TierRouting();

    /** token 用量统计相关开关。 */
    private UsageConfig usage = new UsageConfig();

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String model;
        private boolean visionEnable = false;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialIntervalMs = 500L;
        private double multiplier = 2.0;
        private long maxIntervalMs = 4000L;
    }

    @Data
    public static class TierRouting {
        /** 轻量层（分类 / 内部总结 / 记忆压缩）。 */
        private TierTarget light = new TierTarget();
        /** 多模态视觉层（VisionBridge 用）。 */
        private TierTarget vision = new TierTarget();
    }

    @Data
    public static class TierTarget {
        /** 目标 provider 名；null / 空 → 回退到 activeProvider。 */
        private String provider;
        /** 目标 model；null / 空 → 回退到该 provider 配置的 model。 */
        private String model;

        public boolean isConfigured() {
            return (provider != null && !provider.isBlank()) || (model != null && !model.isBlank());
        }
    }

    @Data
    public static class UsageConfig {
        /**
         * 流式调用是否发送 {@code stream_options.include_usage=true} 以拿到 token 用量末帧。
         * 部分 OpenAI 兼容网关不认这个字段，关掉即不发（流式不再统计，对话不受影响）。
         */
        private boolean streamIncludeUsage = true;
    }
}
