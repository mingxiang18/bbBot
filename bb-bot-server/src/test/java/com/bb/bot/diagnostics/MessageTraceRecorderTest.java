package com.bb.bot.diagnostics;

import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MessageTraceRecorder} 的纯逻辑测试：环形淘汰、按 traceId 串联、聚合统计、过滤查询。
 */
class MessageTraceRecorderTest {

    @AfterEach
    void clearMdc() {
        TraceContext.clear();
    }

    private BbReceiveMessage msg(String messageId, String userId, String text) {
        BbReceiveMessage m = new BbReceiveMessage();
        m.setMessageId(messageId);
        m.setUserId(userId);
        m.setGroupId("g1");
        m.setBotType("QQ");
        m.setMessage(text);
        return m;
    }

    /** 模拟一条消息进入：start traceId → onInbound，返回 traceId 供后续节点使用。 */
    private String inbound(MessageTraceRecorder r, BbReceiveMessage m) {
        String traceId = TraceContext.start(m.getMessageId());
        r.onInbound(traceId, m);
        return traceId;
    }

    @Test
    void ringBufferEvictsOldest() {
        MessageTraceRecorder r = new MessageTraceRecorder(3);
        for (int i = 0; i < 5; i++) {
            inbound(r, msg("m" + i, "u", "t" + i));
            TraceContext.clear();
        }
        assertEquals(3, r.bufferedCount());
        List<MessageTrace> recent = r.recent(10);
        // 最新在前：m4, m3, m2
        assertEquals("t4", recent.get(0).getTextPreview());
        assertEquals("t2", recent.get(2).getTextPreview());
    }

    @Test
    void chainsByTraceIdAndComputesSilent() {
        MessageTraceRecorder r = new MessageTraceRecorder(50);
        // 一条被跳过的消息：inbound → decision SKIP(AUTO_REPLY_DISABLED)
        inbound(r, msg("m1", "u1", "hi"));
        r.onHandler("BbAiChatHandler.aiChatHandle");
        r.onDecision(MessageTrace.DECISION_SKIP, "AUTO_REPLY_DISABLED");

        MessageTrace t = r.recent(1).get(0);
        assertEquals("BbAiChatHandler.aiChatHandle", t.toMap().get("handler"));
        assertEquals("SKIP", t.getDecision());
        assertEquals("AUTO_REPLY_DISABLED", t.getSkipReason());
        assertTrue(t.isSilent());
    }

    @Test
    void statsAggregatesByOutcome() {
        MessageTraceRecorder r = new MessageTraceRecorder(50);

        inbound(r, msg("a", "u1", "x"));
        r.onDecision(MessageTrace.DECISION_REPLY_DIRECT, null);
        r.onReply(MessageTrace.REPLY_SENT, "text", null);
        TraceContext.clear();

        inbound(r, msg("b", "u2", "y"));
        r.onDecision(MessageTrace.DECISION_SKIP, "PROB_MISS");
        TraceContext.clear();

        inbound(r, msg("c", "u3", "z"));
        r.onDecision(MessageTrace.DECISION_REPLY_DIRECT, null);
        r.onReply(MessageTrace.REPLY_FAILED, "send", "boom");
        TraceContext.clear();

        Map<String, Object> s = r.stats(60);
        assertEquals(3, s.get("received"));
        assertEquals(1, s.get("replied"));
        assertEquals(1, s.get("skipped"));
        assertEquals(1, s.get("failed"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> reasons = (Map<String, Integer>) s.get("skipReasons");
        assertEquals(1, reasons.get("PROB_MISS"));
    }

    @Test
    void recentMatchingFiltersByUserAndKeyword() {
        MessageTraceRecorder r = new MessageTraceRecorder(50);
        inbound(r, msg("a", "u1", "hello world"));
        TraceContext.clear();
        inbound(r, msg("b", "u2", "hello there"));
        TraceContext.clear();
        inbound(r, msg("c", "u1", "bye"));
        TraceContext.clear();

        List<MessageTrace> byUser = r.recentMatching("u1", null, null, 10);
        assertEquals(2, byUser.size());

        List<MessageTrace> byKeyword = r.recentMatching(null, null, "hello", 10);
        assertEquals(2, byKeyword.size());

        List<MessageTrace> both = r.recentMatching("u1", null, "hello", 10);
        assertEquals(1, both.size());
        assertEquals("hello world", both.get(0).getTextPreview());
    }

    @Test
    void unknownTraceIdIsIgnored() {
        MessageTraceRecorder r = new MessageTraceRecorder(50);
        // 没有 inbound，直接 onDecision：current() 取不到轨迹，应静默无异常
        TraceContext.start("ghost");
        r.onDecision(MessageTrace.DECISION_SKIP, "X");
        assertEquals(0, r.bufferedCount());
        assertTrue(r.recent(1).isEmpty());
    }
}
