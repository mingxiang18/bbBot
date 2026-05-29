package com.bb.bot.client;

import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AbstractWebSocketGuard} 单测。
 *
 * <p>用伪子类驱动守护循环，验证：</p>
 * <ul>
 *   <li>{@link AbstractWebSocketGuard#handleTick()} 被守护线程周期性调用；</li>
 *   <li>{@link AbstractWebSocketGuard#shouldReconnect(long, long)} 的退避阈值语义；</li>
 *   <li>{@code handleTick} 抛出任意 {@link Throwable}（含 {@link Error}）被吞掉后循环不中断、继续调用。</li>
 * </ul>
 *
 * <p>伪子类从不调用 {@code connect()}，故底层处于 NOT_YET_CONNECTED 状态，
 * {@code isOpen()}/{@code isClosing()} 均为 false，可在无真实网络的环境下确定性地测试守护逻辑。</p>
 */
class AbstractWebSocketGuardTest {

    private static final URI DUMMY_URI = URI.create("ws://localhost:1");

    /**
     * 伪子类：可配置 tick 间隔、tick 行为，并记录被调用次数。
     */
    private static class FakeGuard extends AbstractWebSocketGuard {
        private final long intervalMs;
        private final AtomicInteger tickCount = new AtomicInteger(0);
        /** tick 前若干次抛出的异常（数组长度即抛异常的次数），用于验证“吞异常续跑”。 */
        private volatile int throwFirstN = 0;
        private volatile Throwable throwable;
        private volatile CountDownLatch latch;

        FakeGuard(long intervalMs) {
            super(DUMMY_URI, "fake-ws-guard-");
            this.intervalMs = intervalMs;
        }

        @Override
        protected long interval() {
            return intervalMs;
        }

        @Override
        protected void handleTick() {
            int n = tickCount.incrementAndGet();
            CountDownLatch l = latch;
            if (l != null) {
                l.countDown();
            }
            if (n <= throwFirstN) {
                if (throwable instanceof RuntimeException re) {
                    throw re;
                }
                if (throwable instanceof Error err) {
                    throw err;
                }
            }
        }

        void openGuard() {
            startGuard();
        }

        // ---- WebSocketClient 抽象方法的空实现（本测试不走真实连接）----
        @Override public void onOpen(ServerHandshake handshakedata) { }
        @Override public void onMessage(String message) { }
        @Override public void onClose(int code, String reason, boolean remote) { }
        @Override public void onError(Exception ex) { }

        // 暴露给测试的 shouldReconnect
        boolean reconnect(long lastAttemptAt, long minIntervalMs) {
            return shouldReconnect(lastAttemptAt, minIntervalMs);
        }
    }

    @Test
    void handleTickCalledPeriodically() throws InterruptedException {
        FakeGuard guard = new FakeGuard(10);
        guard.latch = new CountDownLatch(3);
        try {
            guard.openGuard();
            //10ms 间隔下，3 次调用约需 ~20ms+，给足 2s 余量避免 CI 抖动
            boolean reached = guard.latch.await(2, TimeUnit.SECONDS);
            assertThat(reached).as("handleTick 应被周期性调用至少 3 次").isTrue();
            assertThat(guard.tickCount.get()).isGreaterThanOrEqualTo(3);
        } finally {
            guard.stopGuard();
        }
    }

    @Test
    void startGuardIsIdempotent() throws InterruptedException {
        FakeGuard guard = new FakeGuard(10);
        guard.latch = new CountDownLatch(1);
        try {
            guard.openGuard();
            guard.openGuard(); //重复启动不应再起一个线程
            assertThat(guard.latch.await(2, TimeUnit.SECONDS)).isTrue();
            //数线程名前缀为 fake-ws-guard- 的活动线程，应恰为 1
            long count = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.isAlive() && t.getName().startsWith("fake-ws-guard-"))
                    .count();
            assertThat(count).isEqualTo(1);
        } finally {
            guard.stopGuard();
        }
    }

    @Test
    void runtimeExceptionInTickIsSwallowedAndLoopContinues() throws InterruptedException {
        FakeGuard guard = new FakeGuard(10);
        guard.throwable = new IllegalStateException("boom");
        guard.throwFirstN = 2;          //前两次 tick 抛 RuntimeException
        guard.latch = new CountDownLatch(5); //循环必须挺过异常并继续调用到第 5 次
        try {
            guard.openGuard();
            assertThat(guard.latch.await(2, TimeUnit.SECONDS))
                    .as("tick 抛 RuntimeException 后循环应继续调用").isTrue();
            assertThat(guard.tickCount.get()).isGreaterThanOrEqualTo(5);
        } finally {
            guard.stopGuard();
        }
    }

    @Test
    void errorInTickIsSwallowedAndLoopContinues() throws InterruptedException {
        FakeGuard guard = new FakeGuard(10);
        guard.throwable = new OutOfMemoryError("simulated"); //模拟历史上的 OOM Error
        guard.throwFirstN = 2;
        guard.latch = new CountDownLatch(5);
        try {
            guard.openGuard();
            assertThat(guard.latch.await(2, TimeUnit.SECONDS))
                    .as("tick 抛 Error 后循环也应继续调用").isTrue();
            assertThat(guard.tickCount.get()).isGreaterThanOrEqualTo(5);
        } finally {
            guard.stopGuard();
        }
    }

    @Test
    void shouldReconnectFalseWithinBackoffInterval() {
        FakeGuard guard = new FakeGuard(10);
        long now = System.currentTimeMillis();
        //距上次尝试仅 1s，未达 10s 阈值 → 不重连
        assertThat(guard.reconnect(now - 1_000L, 10_000L)).isFalse();
    }

    @Test
    void shouldReconnectTrueAfterBackoffInterval() {
        FakeGuard guard = new FakeGuard(10);
        long now = System.currentTimeMillis();
        //距上次尝试 20s，已超 10s 阈值，且连接未 open/closing → 应重连
        assertThat(guard.reconnect(now - 20_000L, 10_000L)).isTrue();
    }

    @Test
    void shouldReconnectTrueAtExactThreshold() {
        FakeGuard guard = new FakeGuard(10);
        long min = 10_000L;
        //恰好等于阈值（>=）边界 → 应重连
        long lastAttempt = System.currentTimeMillis() - min;
        assertThat(guard.reconnect(lastAttempt, min)).isTrue();
    }

    @Test
    void shouldReconnectFalseWhenFutureTimestamp() {
        FakeGuard guard = new FakeGuard(10);
        //时间戳在“未来”（差值为负）→ 不达阈值 → 不重连
        long future = System.currentTimeMillis() + 60_000L;
        assertThat(guard.reconnect(future, 10_000L)).isFalse();
    }
}
