package com.bb.bot.common.util.aiChat.provider;

/**
 * 将 AI HTTP 调用的状态码归类为 {@link AIException}，供各 Provider 复用。
 *
 * <p>分类规则（与 Anthropic / OpenAI 兼容 Provider 原有 classify 逻辑一致）：
 * <ul>
 *     <li>401 / 403 → {@link AIException.ErrorType#UNAUTHORIZED}</li>
 *     <li>429 → {@link AIException.ErrorType#RATE_LIMITED}</li>
 *     <li>{@code >= 500} → {@link AIException.ErrorType#RETRYABLE}</li>
 *     <li>其余（如 400 / 404）→ {@link AIException.ErrorType#FATAL}</li>
 * </ul>
 *
 * @author ren
 */
public final class HttpErrorClassifier {

    private HttpErrorClassifier() {
    }

    /**
     * 根据 HTTP 状态码构造对应类型的 {@link AIException}。
     *
     * @param status HTTP 状态码
     * @param tag    模型标识（如 {@code kind/model}），用于异常信息
     * @param body   响应体，用于异常信息
     */
    public static AIException classify(int status, String tag, String body) {
        if (status == 401 || status == 403) {
            return new AIException(AIException.ErrorType.UNAUTHORIZED, status,
                    "AI model [" + tag + "] unauthorized: " + body);
        }
        if (status == 429) {
            return new AIException(AIException.ErrorType.RATE_LIMITED, status,
                    "AI model [" + tag + "] rate limited: " + body);
        }
        if (status >= 500) {
            return new AIException(AIException.ErrorType.RETRYABLE, status,
                    "AI model [" + tag + "] server error: " + body);
        }
        return new AIException(AIException.ErrorType.FATAL, status,
                "AI model [" + tag + "] client error: " + body);
    }
}
