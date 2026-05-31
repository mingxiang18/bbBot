package com.bb.bot.dispatcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 入站消息幂等去重。
 *
 * <p>同一条消息（按 {@code messageId}）在 TTL 窗口内只放行一次。用于挡掉「QQ webhook
 * 在被动回复超过其 ~5 秒窗口后重推同一条消息」导致 bot 处理两遍、给用户发两张一模一样图的问题
 * ——QQ 重推时 {@code msg_id} 与首次完全相同，故可据此去重。</p>
 *
 * <p>{@link #firstSeen(String)} 用 Caffeine 的 {@code asMap().putIfAbsent} 做原子判定，
 * 并发到达的重复推送也只会有一个返回 true。TTL 5 分钟（远大于 QQ 的重推间隔），
 * {@code maximumSize} 兜底防内存无界。</p>
 */
@Component
public class InboundMessageDeduplicator {

    private static final Object PRESENT = new Object();

    private final Cache<String, Object> seen = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    /**
     * 首次见到该 messageId 返回 {@code true}（放行处理）；TTL 内重复出现返回 {@code false}（应跳过）。
     * messageId 为空时一律放行（不因缺 id 而误丢消息）。
     */
    public boolean firstSeen(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return true;
        }
        return seen.asMap().putIfAbsent(messageId, PRESENT) == null;
    }
}
