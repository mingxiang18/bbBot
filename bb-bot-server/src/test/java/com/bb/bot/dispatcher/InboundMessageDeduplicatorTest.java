package com.bb.bot.dispatcher;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InboundMessageDeduplicator} 单元测试：验证「同一 messageId 只放行一次」，
 * 这是挡掉 QQ 重推同一条消息导致发两张图的核心。
 */
class InboundMessageDeduplicatorTest {

    @Test
    void firstSeen_sameId_onlyFirstPasses() {
        InboundMessageDeduplicator dedup = new InboundMessageDeduplicator();
        String id = "ROBOT1.0_we.BxbZnkm4pPVAxsBrDBVpmaN3...hmxQ!";

        assertTrue(dedup.firstSeen(id), "首次出现应放行");
        assertFalse(dedup.firstSeen(id), "QQ 重推同一 msg_id 应被拦截");
        assertFalse(dedup.firstSeen(id), "再来还是拦截");
    }

    @Test
    void firstSeen_differentIds_allPass() {
        InboundMessageDeduplicator dedup = new InboundMessageDeduplicator();

        assertTrue(dedup.firstSeen("id-A"));
        assertTrue(dedup.firstSeen("id-B"), "不同消息互不影响");
        assertFalse(dedup.firstSeen("id-A"), "A 已见过");
    }

    @Test
    void firstSeen_nullOrEmpty_alwaysPasses() {
        InboundMessageDeduplicator dedup = new InboundMessageDeduplicator();

        // 缺 id 时不应误丢消息，一律放行
        assertTrue(dedup.firstSeen(null));
        assertTrue(dedup.firstSeen(null));
        assertTrue(dedup.firstSeen(""));
        assertTrue(dedup.firstSeen(""));
    }

    @Test
    void firstSeen_concurrentSameId_exactlyOnePasses() throws Exception {
        // 模拟两次重推几乎同时到达（不同 http 线程）：原子判定必须只有一个放行
        InboundMessageDeduplicator dedup = new InboundMessageDeduplicator();
        String id = "concurrent-msg";
        int threads = 16;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger passed = new AtomicInteger(0);
        Set<Integer> ignored = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (dedup.firstSeen(id)) {
                    passed.incrementAndGet();
                } else {
                    ignored.add(1);
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "并发任务应在超时内完成");

        assertEquals(1, passed.get(), "并发重复推送只能有一个被放行");
    }
}
