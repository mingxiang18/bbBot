package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HttpErrorClassifier} 状态码归类单测：
 * 401/403→UNAUTHORIZED、429→RATE_LIMITED、500/503→RETRYABLE、400/404→FATAL。
 *
 * @author ren
 */
class HttpErrorClassifierTest {

    private static final String TAG = "openai/gpt-4o";
    private static final String BODY = "{\"error\":\"boom\"}";

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void unauthorized(int status) {
        AIException ex = HttpErrorClassifier.classify(status, TAG, BODY);
        assertThat(ex.getErrorType()).isEqualTo(AIException.ErrorType.UNAUTHORIZED);
        assertThat(ex.getHttpStatus()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(TAG).contains(BODY).contains("unauthorized");
        assertThat(ex.isRetryable()).isFalse();
    }

    @Test
    void rateLimited() {
        AIException ex = HttpErrorClassifier.classify(429, TAG, BODY);
        assertThat(ex.getErrorType()).isEqualTo(AIException.ErrorType.RATE_LIMITED);
        assertThat(ex.getHttpStatus()).isEqualTo(429);
        assertThat(ex.getMessage()).contains(TAG).contains(BODY).contains("rate limited");
        assertThat(ex.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void retryable(int status) {
        AIException ex = HttpErrorClassifier.classify(status, TAG, BODY);
        assertThat(ex.getErrorType()).isEqualTo(AIException.ErrorType.RETRYABLE);
        assertThat(ex.getHttpStatus()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(TAG).contains(BODY).contains("server error");
        assertThat(ex.isRetryable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 422})
    void fatal(int status) {
        AIException ex = HttpErrorClassifier.classify(status, TAG, BODY);
        assertThat(ex.getErrorType()).isEqualTo(AIException.ErrorType.FATAL);
        assertThat(ex.getHttpStatus()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(TAG).contains(BODY).contains("client error");
        assertThat(ex.isRetryable()).isFalse();
    }
}
