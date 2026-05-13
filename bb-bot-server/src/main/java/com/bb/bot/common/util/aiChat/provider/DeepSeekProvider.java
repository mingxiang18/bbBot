package com.bb.bot.common.util.aiChat.provider;

import org.springframework.stereotype.Component;

/**
 * DeepSeek 实现。DeepSeek 完全兼容 OpenAI Chat Completion 协议，区别仅在 baseUrl + 默认 model。
 * 这同时验证了 {@link AbstractOpenAICompatibleProvider} 抽象的有效性：
 * 一份请求/解析逻辑可被多家提供商复用。
 *
 * @author ren
 */
@Component
public class DeepSeekProvider extends AbstractOpenAICompatibleProvider {

    public static final String NAME = "deepseek";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected AIProviderProperties.ProviderConfig config() {
        return properties.getDeepseek();
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://api.deepseek.com/v1";
    }

    @Override
    protected String defaultModel() {
        return "deepseek-chat";
    }
}
