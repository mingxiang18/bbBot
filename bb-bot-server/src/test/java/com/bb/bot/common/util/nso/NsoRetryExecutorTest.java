package com.bb.bot.common.util.nso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 {@link NsoRetryExecutor} 的状态码分类与退避重试。
 */
class NsoRetryExecutorTest {

    private NsoRetryExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new NsoRetryExecutor();
        executor.configureForTests(3, 1, 1.0, 1);
    }

    @Test
    void execute_returnsResultWithoutRetryOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute("ok", () -> {
            calls.incrementAndGet();
            return "value";
        });
        assertEquals("value", result);
        assertEquals(1, calls.get());
    }

    @Test
    void execute_classifies401AsUnauthorizedAndDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> executor.execute("auth-test", () -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("RestClientCallCodeException，code: 401, message: bad token");
                }));
        assertSame(Splatoon3ApiException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(401, ex.getHttpStatus());
        assertEquals(1, calls.get(), "401 should not retry");
    }

    @Test
    void execute_classifies403AsUnauthorized() {
        AtomicInteger calls = new AtomicInteger();
        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> executor.execute("auth-test", () -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("RestClientCallCodeException，code: 403, message: forbidden");
                }));
        assertSame(Splatoon3ApiException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(1, calls.get());
    }

    @Test
    void execute_classifies429AsRateLimitedAndRetries() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute("rate-limit-test", () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw new RuntimeException("RestClientCallCodeException，code: 429, message: slow down");
            }
            return "recovered";
        });
        assertEquals("recovered", result);
        assertEquals(2, calls.get());
    }

    @Test
    void execute_classifies500AsRetryableAndExhausts() {
        AtomicInteger calls = new AtomicInteger();
        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> executor.execute("server-error-test", () -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("RestClientCallCodeException，code: 503, message: down");
                }));
        assertSame(Splatoon3ApiException.ErrorType.RETRYABLE, ex.getErrorType());
        assertEquals(3, calls.get(), "should retry up to maxAttempts on 5xx");
    }

    @Test
    void execute_classifies400AsFatalAndDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> executor.execute("client-error-test", () -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("RestClientCallCodeException，code: 400, message: bad");
                }));
        assertSame(Splatoon3ApiException.ErrorType.FATAL, ex.getErrorType());
        assertEquals(1, calls.get());
    }

    @Test
    void execute_unknownStatusBehavesAsRetryable() {
        AtomicInteger calls = new AtomicInteger();
        Splatoon3ApiException ex = assertThrows(Splatoon3ApiException.class,
                () -> executor.execute("network", () -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("connection reset"); // no status code in message
                }));
        assertSame(Splatoon3ApiException.ErrorType.RETRYABLE, ex.getErrorType());
        assertEquals(-1, ex.getHttpStatus());
        assertEquals(3, calls.get());
    }

    @Test
    void extractStatus_returnsNullForBlankMessage() {
        assertNull(NsoRetryExecutor.extractStatus(null));
        assertNull(NsoRetryExecutor.extractStatus(""));
        assertNull(NsoRetryExecutor.extractStatus("no status here"));
    }

    @Test
    void extractStatus_parsesCodeFromMessage() {
        assertEquals(401, NsoRetryExecutor.extractStatus("error code: 401, blah"));
        assertEquals(503, NsoRetryExecutor.extractStatus("RestClientCallCodeException，code: 503, message: down"));
    }
}
