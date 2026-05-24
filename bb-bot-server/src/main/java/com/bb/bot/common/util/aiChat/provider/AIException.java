package com.bb.bot.common.util.aiChat.provider;

import lombok.Getter;

/**
 * AI 调用异常。区分错误类型以决定是否重试 / 是否需要刷新凭证。
 *
 * @author ren
 */
@Getter
public class AIException extends RuntimeException {

    public enum ErrorType {
        /** 鉴权失败，立即抛出，不重试。 */
        UNAUTHORIZED,
        /** 限流，可退避后重试。 */
        RATE_LIMITED,
        /** 5xx 等可重试错误。 */
        RETRYABLE,
        /** 4xx（除 401/429）等不可重试错误。 */
        FATAL
    }

    private final ErrorType errorType;
    private final int httpStatus;

    public AIException(ErrorType errorType, String message) {
        this(errorType, -1, message, null);
    }

    public AIException(ErrorType errorType, String message, Throwable cause) {
        this(errorType, -1, message, cause);
    }

    public AIException(ErrorType errorType, int httpStatus, String message) {
        this(errorType, httpStatus, message, null);
    }

    public AIException(ErrorType errorType, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public boolean isRetryable() {
        return errorType == ErrorType.RETRYABLE || errorType == ErrorType.RATE_LIMITED;
    }
}
