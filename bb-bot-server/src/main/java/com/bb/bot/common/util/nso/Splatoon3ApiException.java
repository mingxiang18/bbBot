package com.bb.bot.common.util.nso;

import lombok.Getter;

/**
 * Splatoon3 / NSO API 调用异常。区分错误类型让 handler 决定是否需要刷新 token / 退避重试。
 *
 * @author ren
 */
@Getter
public class Splatoon3ApiException extends RuntimeException {

    public enum ErrorType {
        /** 401 / 403：token 过期或权限不足，handler 应该触发刷新流程。 */
        UNAUTHORIZED,
        /** 429：限流，可退避后重试。 */
        RATE_LIMITED,
        /** 5xx 等可重试错误。 */
        RETRYABLE,
        /** 4xx（除 401/403/429）等不可重试错误。 */
        FATAL
    }

    private final ErrorType errorType;
    private final int httpStatus;

    public Splatoon3ApiException(ErrorType errorType, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
    }

    public boolean isRetryable() {
        return errorType == ErrorType.RETRYABLE || errorType == ErrorType.RATE_LIMITED;
    }
}
