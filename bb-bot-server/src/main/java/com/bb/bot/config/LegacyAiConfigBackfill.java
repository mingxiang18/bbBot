package com.bb.bot.config;

import com.bb.bot.common.util.aiChat.provider.AIProviderProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 兼容旧版 {@code chatGPT.*} 配置：如果新 {@code ai.openai.*} 没填、但旧的填了，
 * 就把旧值回填到 openai provider 配置上，并打 WARN 提示用户迁移。
 *
 * <p>用一个 release 的兼容期；后续可以删除该类。
 *
 * @author ren
 */
@Slf4j
@Component
public class LegacyAiConfigBackfill {

    @Autowired
    private Environment environment;

    @Autowired
    private AIProviderProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        AIProviderProperties.ProviderConfig openai = properties.getOpenai();
        boolean changed = false;

        String legacyKey = environment.getProperty("chatGPT.apiKey");
        if (StringUtils.isBlank(openai.getApiKey()) && StringUtils.isNotBlank(legacyKey)) {
            openai.setApiKey(legacyKey);
            changed = true;
        }
        String legacyUrl = environment.getProperty("chatGPT.url");
        if (StringUtils.isBlank(openai.getBaseUrl()) && StringUtils.isNotBlank(legacyUrl)) {
            openai.setBaseUrl(stripTrailingChatCompletions(legacyUrl));
            changed = true;
        }
        String legacyModel = environment.getProperty("chatGPT.model");
        if (StringUtils.isBlank(openai.getModel()) && StringUtils.isNotBlank(legacyModel)) {
            openai.setModel(legacyModel);
            changed = true;
        }
        Boolean legacyVision = environment.getProperty("chatGPT.visionEnable", Boolean.class);
        if (legacyVision != null && !openai.isVisionEnable()) {
            openai.setVisionEnable(legacyVision);
            changed = true;
        }

        if (changed) {
            log.warn("Legacy [chatGPT.*] config detected and backfilled to [ai.openai.*]. " +
                    "Please migrate your application.yml to the new format; legacy keys will be removed in a future release.");
        }
    }

    /**
     * 旧配置里的 url 是完整的 chat completions endpoint，新配置只要 baseUrl，
     * 截掉 {@code /chat/completions} 后缀让两份配置语义一致。
     */
    private static String stripTrailingChatCompletions(String url) {
        if (url.endsWith("/chat/completions")) {
            return url.substring(0, url.length() - "/chat/completions".length());
        }
        return url;
    }
}
