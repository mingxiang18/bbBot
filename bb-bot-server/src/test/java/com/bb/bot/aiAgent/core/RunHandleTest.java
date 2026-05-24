package com.bb.bot.aiAgent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 {@link RunHandle} 的 steering 语义，重点是「停止瞬间塞进来的消息不丢」。
 */
class RunHandleTest {

    @Test
    void offerThenDrain_returnsInOrder() {
        RunHandle h = new RunHandle();
        assertTrue(h.offer("a"));
        assertTrue(h.offer("b"));
        assertEquals(List.of("a", "b"), h.drain());
        assertTrue(h.drain().isEmpty(), "drain 后队列应清空");
    }

    @Test
    void drainOrClose_emptyQueue_closesAndRejectsFurtherOffers() {
        RunHandle h = new RunHandle();
        assertTrue(h.drainOrClose().isEmpty(), "空队列 drainOrClose 返回空");
        assertFalse(h.offer("late"), "关闭后 offer 应失败，调用方据此另起新 run");
    }

    @Test
    void drainOrClose_nonEmpty_returnsMessagesAndStaysOpen() {
        RunHandle h = new RunHandle();
        h.offer("steer");
        assertEquals(List.of("steer"), h.drainOrClose(), "非空时返回待处理消息");
        assertTrue(h.offer("more"), "非空 drainOrClose 不关闭，仍可继续 steer");
    }

    @Test
    void close_returnsLeftoverAndRejectsFurtherOffers() {
        RunHandle h = new RunHandle();
        h.offer("x");
        h.offer("y");
        assertEquals(List.of("x", "y"), h.close(), "close 返回残留消息供补派");
        assertFalse(h.offer("z"), "close 后 offer 失败");
        assertTrue(h.drain().isEmpty());
    }

    @Test
    void close_emptyQueue_returnsEmpty() {
        RunHandle h = new RunHandle();
        assertTrue(h.close().isEmpty());
    }
}
