package com.bb.bot.common.util.aiChat.provider;

import lombok.Data;

/**
 * 一个具体模型的接入参数。{@code ai.models.<name>} 绑定到本类，由 {@code ai.roles} 把
 * heavy / light / vision 角色指向某个命名模型。
 *
 * <p>同一厂商可命名多个（如 deepseek-reasoner 当 heavy、deepseek-chat 当 light），互不影响。</p>
 *
 * @author ren
 */
@Data
public class ModelSpec {

    /** 命名（= ai.models 的 key），由 AiChatService 回填，仅用于日志。 */
    private String name;

    /** OpenAI 兼容 endpoint 根，如 https://api.deepseek.com/v1。 */
    private String baseUrl;

    private String apiKey;

    /** 实际请求体里的 model 名，如 deepseek-chat / moonshot-v1-8k。 */
    private String model;

    /**
     * 厂商种类，用于：① API 兼容性差异（moonshot 不收网络图 URL，需转 base64）；
     * ② token 用量 / 计费的 provider 归属（与 ai_model_pricing.provider_name 对应）。
     * 取值如 openai / deepseek / moonshot / anthropic。默认 openai。
     */
    private String kind = "openai";

    /** 该模型是否支持视觉（图片输入）。 */
    private boolean vision = false;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }
}
