package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆卡片生命周期归属人：唯一负责把 active 卡片降级为 stale 的地方（落地清单 Phase 2）。
 *
 * <p>两种降级触发：</p>
 * <ul>
 *   <li>过期：{@code expires_at} 已过（project_state / 临时事件等）</li>
 *   <li>长期未确认：{@code last_seen_at} 早于 staleAfterDays 之前</li>
 * </ul>
 *
 * <p>不删除、不晋升——晋升（stale→active）只在 refresh/再次命中时由 {@link MemoryPolicy} 处理。</p>
 */
@Slf4j
@Component
public class MemoryLifecycleSweeper {

    @Autowired
    private IAiMemoryItemService itemService;

    /** 超过这么多天没被再次确认（last_seen_at）就降级 stale。 */
    @Value("${aiAgent.memory.staleAfterDays:60}")
    private int staleAfterDays;

    @Scheduled(fixedDelay = 600_000L, initialDelay = 120_000L)
    public void sweep() {
        try {
            LocalDateTime now = LocalDateTime.now();
            // 1) 过期：active 且 expires_at < now
            List<AiMemoryItem> expired = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                    .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.code())
                    .isNotNull(AiMemoryItem::getExpiresAt)
                    .lt(AiMemoryItem::getExpiresAt, now)
                    .last("limit 200"));
            // 2) 长期未确认：active 且 last_seen_at < now - staleAfterDays
            LocalDateTime staleThreshold = now.minusDays(staleAfterDays);
            List<AiMemoryItem> unseen = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                    .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.code())
                    .isNotNull(AiMemoryItem::getLastSeenAt)
                    .lt(AiMemoryItem::getLastSeenAt, staleThreshold)
                    .last("limit 200"));

            int n = 0;
            for (AiMemoryItem it : concat(expired, unseen)) {
                it.setStatus(MemoryStatus.STALE.code());
                itemService.updateById(it);
                n++;
            }
            if (n > 0) {
                log.info("MemoryLifecycleSweeper 降级 stale {} 条", n);
            }
        } catch (Exception e) {
            log.warn("MemoryLifecycleSweeper sweep 异常", e);
        }
    }

    private static List<AiMemoryItem> concat(List<AiMemoryItem> a, List<AiMemoryItem> b) {
        a.addAll(b);
        return a;
    }
}
