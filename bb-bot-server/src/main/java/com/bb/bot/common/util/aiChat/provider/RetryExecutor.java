package com.bb.bot.common.util.aiChat.provider;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 指数退避通用重试器。把原本散落在 {@link AnthropicProvider}/{@link OpenAiCompatProvider} 的
 * {@code executeWithRetry}/{@code executeStreamWithRetry} 循环抽出来统一，语义保持完全一致：
 *
 * <ul>
 *   <li>从第 1 次起最多尝试 {@code maxAttempts} 次；首次成功立即返回，不再退避。</li>
 *   <li>仅当 {@link AIException#isRetryable()} 为 {@code true} 且未到最后一次时才退避重试；
 *       否则（不可重试 / 已是最后一次）立即把该次异常抛出。</li>
 *   <li>退避时长指数增长：{@code interval = min(interval * multiplier, maxInterval)}，
 *       初值为 {@code initialInterval}。</li>
 * </ul>
 *
 * <p>{@code onTerminalError} 回调用于流式场景在抛出前通知 {@code StreamHandler.onError}；
 * 阻塞场景传 {@code null} 即可。</p>
 *
 * @author ren
 */
@Slf4j
public final class RetryExecutor {

    /** 退避休眠实现，可注入以便测试不真正阻塞。默认沿用原 Provider 的 {@code sleep} 语义。 */
    private final Sleeper sleeper;

    public RetryExecutor() {
        this(RetryExecutor::defaultSleep);
    }

    public RetryExecutor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * 执行 {@code task}，按 {@code retry} 配置做指数退避重试。
     *
     * @param retry           重试配置（最大次数 / 初始间隔 / 倍率 / 上限间隔）
     * @param task            实际任务，失败须抛 {@link AIException}
     * @param onRetry         每次决定重试（退避前）调用，用于打印日志；参数为当前尝试序号、待退避毫秒、本次异常
     * @param onTerminalError 终止（不可重试或已耗尽次数）抛出前调用，可为 {@code null}
     * @param <T>             返回类型
     * @return task 的返回值
     * @throws AIException 终止时抛出最后一次异常
     */
    public <T> T execute(AIProviderProperties.RetryConfig retry,
                         Supplier<T> task,
                         RetryListener onRetry,
                         java.util.function.Consumer<AIException> onTerminalError) {
        long interval = retry.getInitialIntervalMs();
        AIException last = null;
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            try {
                return task.get();
            } catch (AIException e) {
                last = e;
                if (!e.isRetryable() || attempt == retry.getMaxAttempts()) {
                    if (onTerminalError != null) {
                        onTerminalError.accept(e);
                    }
                    throw e;
                }
                if (onRetry != null) {
                    onRetry.onRetry(attempt, interval, e);
                }
                sleeper.sleep(interval);
                interval = nextInterval(interval, retry);
            }
        }
        // 理论不可达（循环内已抛出），与原实现保持一致：兜底再抛最后一次异常。
        if (last != null) {
            if (onTerminalError != null) {
                onTerminalError.accept(last);
            }
            throw last;
        }
        return null;
    }

    /**
     * 计算下一次退避间隔：{@code min(interval * multiplier, maxInterval)}，与原 Provider 完全一致
     * （含 {@code (long)} 截断）。
     */
    public static long nextInterval(long interval, AIProviderProperties.RetryConfig retry) {
        return Math.min((long) (interval * retry.getMultiplier()), retry.getMaxIntervalMs());
    }

    private static void defaultSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 退避休眠抽象，便于测试替换。 */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long ms);
    }

    /** 重试前回调（用于日志）。 */
    @FunctionalInterface
    public interface RetryListener {
        void onRetry(int attempt, long intervalMs, AIException error);
    }
}
