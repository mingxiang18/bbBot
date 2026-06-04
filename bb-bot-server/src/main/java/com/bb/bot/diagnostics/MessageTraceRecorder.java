package com.bb.bot.diagnostics;

import com.bb.bot.entity.bb.BbReceiveMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息处理轨迹的内存环形缓冲。
 *
 * <p>记录最近 N 条入站消息从「收到 → 去重 → 命中 handler → 决策 → 发送」的全过程，常驻内存、
 * 不依赖 DB，因此即便数据库故障也能即时回答「为什么没回复」。是 owner 对话式自查
 * （{@code self_check} / {@code trace_message} 工具）的数据基础。</p>
 *
 * <p>更新类方法（{@link #onHandler}/{@link #onDecision}/{@link #onReply} 等）从
 * {@link TraceContext#current()} 取当前线程 traceId，调用方无需显式传递；traceId 缺失
 * 或对应轨迹已被淘汰时静默忽略（诊断信号尽力而为，绝不影响主流程）。</p>
 */
@Component
public class MessageTraceRecorder {

    private final int capacity;
    private final ConcurrentLinkedDeque<MessageTrace> order = new ConcurrentLinkedDeque<>();
    private final Map<String, MessageTrace> byId = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger();

    public MessageTraceRecorder(@Value("${diag.trace.capacity:200}") int capacity) {
        this.capacity = capacity > 0 ? capacity : 200;
    }

    /** 入站时创建轨迹。traceId 已由 {@link TraceContext} 写入 MDC。 */
    public void onInbound(String traceId, BbReceiveMessage msg) {
        if (StringUtils.isBlank(traceId) || msg == null) {
            return;
        }
        String userName = msg.getSender() == null ? null : msg.getSender().getNickname();
        MessageTrace trace = new MessageTrace(traceId, LocalDateTime.now(),
                msg.getBotType(), str(msg.getMessageType()), msg.getGroupId(), msg.getUserId(),
                userName, preview(msg.getMessage()));
        byId.put(traceId, trace);
        order.addLast(trace);
        size.incrementAndGet();
        // 超容量则从头部淘汰最旧轨迹
        while (size.get() > capacity) {
            MessageTrace evicted = order.pollFirst();
            if (evicted == null) {
                break;
            }
            byId.remove(evicted.getTraceId());
            size.decrementAndGet();
        }
    }

    /** 标记为重复入站（被去重器拦下）。 */
    public void onDuplicate(String traceId) {
        MessageTrace t = byId.get(traceId);
        if (t != null) {
            t.setDuplicate(true);
        }
    }

    /** 命中并执行了某 handler。 */
    public void onHandler(String handlerName) {
        MessageTrace t = current();
        if (t != null) {
            t.appendHandler(handlerName);
        }
    }

    /** 回复决策（REPLY_DIRECT / REPLY / SKIP）+ 跳过原因。 */
    public void onDecision(String decision, String skipReason) {
        MessageTrace t = current();
        if (t != null) {
            t.setDecision(decision);
            if (skipReason != null) {
                t.setSkipReason(skipReason);
            }
        }
    }

    /** 发送结果（SENT / FAILED），用当前线程 MDC 的 traceId 定位轨迹。 */
    public void onReply(String status, String via, String error) {
        MessageTrace t = current();
        if (t != null) {
            t.setReply(status, via, error);
        }
    }

    /**
     * 发送结果（按显式 traceId 定位）。供流式 / 工具循环回复在非 MDC 线程的 onComplete 回调里使用——
     * 这些回调可能跑在 provider 的流线程上，MDC traceId 不一定还在，但 traceId 是 messageId 的纯函数
     * （{@link TraceContext#newId}），可由 messageId 重算后定位同一条轨迹。
     */
    public void onReply(String traceId, String status, String via, String error) {
        if (traceId == null) {
            return;
        }
        MessageTrace t = byId.get(traceId);
        if (t != null) {
            t.setReply(status, via, error);
        }
    }

    private MessageTrace current() {
        String traceId = TraceContext.current();
        return traceId == null ? null : byId.get(traceId);
    }

    /** 最近 n 条轨迹（最新在前）。 */
    public List<MessageTrace> recent(int n) {
        List<MessageTrace> all = new ArrayList<>(order);
        Collections.reverse(all);
        return all.size() > n ? all.subList(0, n) : all;
    }

    /** 按条件过滤最近轨迹：用户、距今分钟数、关键字（任一为空则不限）。 */
    public List<MessageTrace> recentMatching(String userId, Integer minutesAgo, String keyword, int n) {
        LocalDateTime since = minutesAgo == null ? null : LocalDateTime.now().minusMinutes(minutesAgo);
        List<MessageTrace> out = new ArrayList<>();
        List<MessageTrace> all = new ArrayList<>(order);
        Collections.reverse(all);
        for (MessageTrace t : all) {
            if (userId != null && !userId.equals(t.getUserId())) {
                continue;
            }
            if (since != null && t.getReceivedAt() != null && t.getReceivedAt().isBefore(since)) {
                continue;
            }
            if (StringUtils.isNotBlank(keyword)
                    && (t.getTextPreview() == null || !t.getTextPreview().contains(keyword))) {
                continue;
            }
            out.add(t);
            if (out.size() >= n) {
                break;
            }
        }
        return out;
    }

    /** 最近 minutes 分钟的聚合统计：收到/回复/跳过/失败计数 + 跳过原因分布。 */
    public Map<String, Object> stats(int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        int received = 0, replied = 0, skipped = 0, failed = 0, duplicate = 0;
        Map<String, Integer> skipReasons = new LinkedHashMap<>();
        LocalDateTime lastReceivedAt = null;
        for (MessageTrace t : order) {
            if (t.getReceivedAt() == null || t.getReceivedAt().isBefore(since)) {
                continue;
            }
            received++;
            if (lastReceivedAt == null || t.getReceivedAt().isAfter(lastReceivedAt)) {
                lastReceivedAt = t.getReceivedAt();
            }
            if (t.isDuplicate()) {
                duplicate++;
            }
            if (MessageTrace.REPLY_SENT.equals(t.getReplyStatus())) {
                replied++;
            } else if (MessageTrace.REPLY_FAILED.equals(t.getReplyStatus())) {
                failed++;
            }
            if (MessageTrace.DECISION_SKIP.equals(t.getDecision())) {
                skipped++;
                String r = t.getSkipReason() == null ? "unknown" : t.getSkipReason();
                skipReasons.merge(r, 1, Integer::sum);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("windowMinutes", minutes);
        m.put("received", received);
        m.put("replied", replied);
        m.put("skipped", skipped);
        m.put("failed", failed);
        m.put("duplicate", duplicate);
        m.put("skipReasons", skipReasons);
        m.put("lastReceivedAt", lastReceivedAt == null ? null : lastReceivedAt.toString());
        return m;
    }

    /** 当前缓冲条数（含未过期与过期）。 */
    public int bufferedCount() {
        return order.size();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60) + "…";
    }
}
