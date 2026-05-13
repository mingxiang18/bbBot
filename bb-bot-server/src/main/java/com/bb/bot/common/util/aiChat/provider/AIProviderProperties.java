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
}
