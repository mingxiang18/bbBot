package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RetryExecutor} 全分支单测：
 * 首次即成功、可重试达上限、不可重试立即抛、退避曲线 min(interval*mult,max)、回调时机。
 *
 * @author ren
 */
class RetryExecutorTest {

    private static AIProviderProperties.RetryConfig config(int maxAttempts, long initial,
                                                           double multiplier, long maxInterval) {
        AIProviderProperties.RetryConfig cfg = new AIProviderProperties.RetryConfig();
        cfg.setMaxAttempts(maxAttempts);
        cfg.setInitialIntervalMs(initial);
        cfg.setMultiplier(multiplier);
        cfg.setMaxIntervalMs(maxInterval);
        return cfg;
    }

    private static AIException retryable() {
        return new AIException(AIException.ErrorType.RETRYABLE, 503, "boom");
    }

    private static AIException rateLimited() {
        return new AIException(AIException.ErrorType.RATE_LIMITED, 429, "slow down");
    }

    private static AIException fatal() {
        return new AIException(AIException.ErrorType.FATAL, 400, "bad request");
    }

    private static AIException unauthorized() {
        return new AIException(AIException.ErrorType.UNAUTHORIZED, 401, "no key");
    }

    /** 首次即成功：task 只调用 1 次，不退避、不触发任何回调。 */
    @Test
    void firstAttemptSucceedsImmediately() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        List<Integer> retries = new ArrayList<>();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        String result = executor.execute(config(3, 500, 2.0, 4000),
                () -> {
                    calls.incrementAndGet();
                    return "ok";
                },
                (attempt, ms, err) -> retries.add(attempt),
                null);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
        assertThat(retries).isEmpty();
    }

    /** 失败几次后成功：第 3 次成功 -> 共调用 3 次，退避 2 次。 */
    @Test
    void retriesThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        String result = executor.execute(config(5, 500, 2.0, 4000),
                () -> {
                    if (calls.incrementAndGet() < 3) {
                        throw retryable();
                    }
                    return "done";
                },
                null, null);

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(3);
        // 两次退避：500 -> 1000
        assertThat(sleeps).containsExactly(500L, 1000L);
    }

    /** 可重试但耗尽 maxAttempts：抛出最后一次异常；退避 maxAttempts-1 次；最后一次不退避。 */
    @Test
    void retryableExhaustsMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        List<AIException> terminal = new ArrayList<>();
        AIException toThrow = retryable();

        RetryExecutor executor = new RetryExecutor(sleeps::add);

        assertThatThrownBy(() -> executor.execute(config(3, 500, 2.0, 4000),
                () -> {
                    calls.incrementAndGet();
                    throw toThrow;
                },
                null,
                terminal::add))
                .isSameAs(toThrow);

        assertThat(calls).hasValue(3);
        // 退避发生在前两次失败后，第 3 次（最后一次）不退避
        assertThat(sleeps).containsExactly(500L, 1000L);
        assertThat(terminal).containsExactly(toThrow);
    }

    /** RATE_LIMITED 同样可重试。 */
    @Test
    void rateLimitedIsRetryable() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        assertThatThrownBy(() -> executor.execute(config(2, 100, 3.0, 9999),
                () -> {
                    calls.incrementAndGet();
                    throw rateLimited();
                },
                null, null))
                .isInstanceOf(AIException.class);

        assertThat(calls).hasValue(2);
        assertThat(sleeps).containsExactly(100L);
    }

    /** 不可重试（FATAL）：第一次就抛出，task 仅 1 次，不退避，触发 terminal 回调。 */
    @Test
    void fatalNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        List<AIException> terminal = new ArrayList<>();
        AIException toThrow = fatal();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        assertThatThrownBy(() -> executor.execute(config(5, 500, 2.0, 4000),
                () -> {
                    calls.incrementAndGet();
                    throw toThrow;
                },
                null,
                terminal::add))
                .isSameAs(toThrow);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
        assertThat(terminal).containsExactly(toThrow);
    }

    /** 不可重试（UNAUTHORIZED）：同样立即抛。 */
    @Test
    void unauthorizedNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        assertThatThrownBy(() -> executor.execute(config(5, 500, 2.0, 4000),
                () -> {
                    calls.incrementAndGet();
                    throw unauthorized();
                },
                null, null))
                .isInstanceOf(AIException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    /** 退避曲线 min(interval*multiplier, maxInterval)：到达上限后保持上限。 */
    @Test
    void backoffCurveCappedAtMax() {
        List<Long> sleeps = new ArrayList<>();
        AIException toThrow = retryable();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        // initial=500, mult=2, max=4000, attempts=6 -> 退避 5 次
        // 500 -> 1000 -> 2000 -> 4000 -> 4000(被 min 截断)
        assertThatThrownBy(() -> executor.execute(config(6, 500, 2.0, 4000),
                () -> { throw toThrow; },
                null, null))
                .isSameAs(toThrow);

        assertThat(sleeps).containsExactly(500L, 1000L, 2000L, 4000L, 4000L);
    }

    /** multiplier 截断为 long：1.5 倍带小数时 (long) 截断与原实现一致。 */
    @Test
    void backoffMultiplierTruncatesToLong() {
        List<Long> sleeps = new ArrayList<>();
        AIException toThrow = retryable();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        // initial=3, mult=1.5, max=9999, attempts=4 -> 退避 3 次
        // 3 -> (long)(3*1.5)=4 -> (long)(4*1.5)=6
        assertThatThrownBy(() -> executor.execute(config(4, 3, 1.5, 9999),
                () -> { throw toThrow; },
                null, null))
                .isSameAs(toThrow);

        assertThat(sleeps).containsExactly(3L, 4L, 6L);
    }

    /** onRetry 回调：每次决定重试时按序传入 attempt 序号与待退避间隔。 */
    @Test
    void onRetryCallbackReceivesAttemptAndInterval() {
        List<Integer> attempts = new ArrayList<>();
        List<Long> intervals = new ArrayList<>();
        AIException toThrow = retryable();

        RetryExecutor executor = new RetryExecutor(ms -> { });
        assertThatThrownBy(() -> executor.execute(config(3, 500, 2.0, 4000),
                () -> { throw toThrow; },
                (attempt, ms, err) -> {
                    attempts.add(attempt);
                    intervals.add(ms);
                    assertThat(err).isSameAs(toThrow);
                },
                null))
                .isSameAs(toThrow);

        // 仅前两次失败触发 onRetry（第 3 次是终止）
        assertThat(attempts).containsExactly(1, 2);
        assertThat(intervals).containsExactly(500L, 1000L);
    }

    /** maxAttempts=1：无重试机会，可重试异常也直接抛、不退避。 */
    @Test
    void singleAttemptNoRetryEvenIfRetryable() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        AIException toThrow = retryable();

        RetryExecutor executor = new RetryExecutor(sleeps::add);
        assertThatThrownBy(() -> executor.execute(config(1, 500, 2.0, 4000),
                () -> {
                    calls.incrementAndGet();
                    throw toThrow;
                },
                null, null))
                .isSameAs(toThrow);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    /** terminal 回调为 null 时不抛 NPE（阻塞场景用法）。 */
    @Test
    void nullTerminalCallbackIsSafe() {
        RetryExecutor executor = new RetryExecutor(ms -> { });
        AIException toThrow = fatal();
        assertThatThrownBy(() -> executor.execute(config(3, 500, 2.0, 4000),
                () -> { throw toThrow; },
                null, null))
                .isSameAs(toThrow);
    }

    /** nextInterval 纯函数：直接验证 min 截断与 long 截断。 */
    @Test
    void nextIntervalPureFunction() {
        AIProviderProperties.RetryConfig cfg = config(3, 0, 2.0, 4000);
        assertThat(RetryExecutor.nextInterval(500, cfg)).isEqualTo(1000L);
        assertThat(RetryExecutor.nextInterval(3000, cfg)).isEqualTo(4000L); // min 截断
        AIProviderProperties.RetryConfig cfg15 = config(3, 0, 1.5, 9999);
        assertThat(RetryExecutor.nextInterval(3, cfg15)).isEqualTo(4L); // (long)4.5 -> 4
    }

    /** 默认构造（真实 Thread.sleep）：用极短间隔验证仍按曲线工作且最终返回。 */
    @Test
    void defaultConstructorUsesRealSleepAndWorks() {
        AtomicInteger calls = new AtomicInteger();
        RetryExecutor executor = new RetryExecutor();
        String result = executor.execute(config(3, 1, 1.0, 1),
                () -> {
                    if (calls.incrementAndGet() < 2) {
                        throw retryable();
                    }
                    return "ok";
                },
                null, null);
        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }
}
