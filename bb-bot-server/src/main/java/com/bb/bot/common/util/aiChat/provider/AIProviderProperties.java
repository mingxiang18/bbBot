package com.bb.bot.common.util.aiChat.provider;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 调用全局配置：命名模型表 + 角色绑定 + 重试 + 用量开关。
 *
 * <pre>
 * ai:
 *   models:
 *     ds-pro:  { base-url: ..., api-key: ..., model: deepseek-reasoner, kind: deepseek }
 *     ds-flash:{ base-url: ..., api-key: ..., model: deepseek-chat,     kind: deepseek }
 *     kimi-v:  { base-url: ..., api-key: ..., model: moonshot-v1-8k-vision, kind: moonshot, vision: true }
 *   roles:
 *     heavy:  ds-pro     # 干活 / 复杂任务
 *     light:  ds-flash   # 闲聊 / 简单 / 内部总结
 *     vision: kimi-v     # 主模型无视觉时识图
 * </pre>
 *
 * @author ren
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProviderProperties {

    /** 命名模型表：key = 模型名（被 roles 引用）。 */
    private Map<String, ModelSpec> models = new LinkedHashMap<>();

    /** 角色 → 模型名。 */
    private Roles roles = new Roles();

    private RetryConfig retry = new RetryConfig();

    private UsageConfig usage = new UsageConfig();

    @Data
    public static class Roles {
        /** 重模型：面向用户干活 / 复杂任务、工具循环。必填。 */
        private String heavy;
        /** 轻模型：闲聊 / 简单问答 / 廉价分类 / 内部总结。缺省回退 heavy。 */
        private String light;
        /** 视觉模型：主模型无视觉时识图。缺省则不启用视觉桥接。 */
        private String vision;
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialIntervalMs = 500L;
        private double multiplier = 2.0;
        private long maxIntervalMs = 4000L;
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
