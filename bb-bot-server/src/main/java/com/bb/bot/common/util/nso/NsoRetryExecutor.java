package com.bb.bot.common.util.nso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 Splatoon3 / NSO 接口调用包成"分类异常 + 退避重试"。
 *
 * <p>RestUtils 在非 2xx 时抛出形如 {@code "RestClientCallCodeException，code: 401, message: ..."}
 * 的 RuntimeException，本类通过正则解出 status 后归类。
 *
 * @author ren
 */
@Slf4j
@Component
public class NsoRetryExecutor {

    private static final Pattern STATUS_PATTERN = Pattern.compile("code:\\s*(\\d+)");

    @Value("${splatoon.retry.max-attempts:3}")
    private int maxAttempts = 3;

    @Value("${splatoon.retry.initial-interval-ms:800}")
    private long initialIntervalMs = 800L;

    @Value("${splatoon.retry.multiplier:2.0}")
    private double multiplier = 2.0;

    @Value("${splatoon.retry.max-interval-ms:5000}")
    private long maxIntervalMs = 5000L;

    /**
     * 执行一次需要重试保护的 API 调用。
     *
     * @param description 描述（用于日志）
     * @param call 真正的 HTTP 调用，{@link Action#run()} 抛出的运行时异常会被分类。
     * @param <T> 返回类型
     * @return 调用结果
     * @throws Splatoon3ApiException 失败分类后的异常
     */
    public <T> T execute(String description, Action<T> call) {
        long interval = initialIntervalMs;
        Splatoon3ApiException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.run();
            } catch (RuntimeException e) {
                Splatoon3ApiException classified = classify(description, e);
                last = classified;
                if (!classified.isRetryable() || attempt == maxAttempts) {
                    throw classified;
                }
                log.warn("[{}] failed (attempt {}/{}, type={}, status={}). Retrying in {}ms",
                        description, attempt, maxAttempts,
                        classified.getErrorType(), classified.getHttpStatus(), interval, classified);
                sleep(interval);
                interval = Math.min((long) (interval * multiplier), maxIntervalMs);
            }
        }
        throw last;
    }

    /** package-private for tests */
    Splatoon3ApiException classify(String description, RuntimeException e) {
        Integer status = extractStatus(e.getMessage());
        if (status == null) {
            // 网络层 / 解析层异常等同于可重试
            return new Splatoon3ApiException(Splatoon3ApiException.ErrorType.RETRYABLE,
                    -1, "[" + description + "] " + e.getMessage(), e);
        }
        if (status == 401 || status == 403) {
            return new Splatoon3ApiException(Splatoon3ApiException.ErrorType.UNAUTHORIZED,
                    status, "[" + description + "] unauthorized", e);
        }
        if (status == 429) {
            return new Splatoon3ApiException(Splatoon3ApiException.ErrorType.RATE_LIMITED,
                    status, "[" + description + "] rate limited", e);
        }
        if (status >= 500) {
            return new Splatoon3ApiException(Splatoon3ApiException.ErrorType.RETRYABLE,
                    status, "[" + description + "] server error", e);
        }
        return new Splatoon3ApiException(Splatoon3ApiException.ErrorType.FATAL,
                status, "[" + description + "] client error", e);
    }

    /** package-private for tests */
    static Integer extractStatus(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = STATUS_PATTERN.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 测试用：直接覆盖重试参数。 */
    void configureForTests(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {
        this.maxAttempts = maxAttempts;
        this.initialIntervalMs = initialIntervalMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface Action<T> {
        T run();
    }
}
