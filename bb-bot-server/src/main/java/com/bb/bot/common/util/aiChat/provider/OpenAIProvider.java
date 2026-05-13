package com.bb.bot.common.util.aiChat.provider;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI 官方 API 实现。也兼容 OpenAI Chat Completion 协议但需要"先把网络图下到 base64"的模型族
 * （比如 moonshot），通过 model 名识别后走子类逻辑。
 *
 * @author ren
 */
@Component
public class OpenAIProvider extends AbstractOpenAICompatibleProvider {

    public static final String NAME = "openai";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected AIProviderProperties.ProviderConfig config() {
        return properties.getOpenai();
    }

    @Override
    protected String defaultBaseUrl() {
        return "https://api.openai.com/v1";
    }

    @Override
    protected String defaultModel() {
        return "gpt-4";
    }

    @Override
    protected void preprocessImages(List<ChatMessage> messages) {
        // moonshot 系列模型不接受网络图片 URL，必须先转 base64
        String model = config().getModel();
        if (model != null && model.contains("moonshot")) {
            convertNetImagesToBase64(messages);
        }
    }
}
