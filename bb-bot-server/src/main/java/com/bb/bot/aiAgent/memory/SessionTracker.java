package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.service.IAiMemorySessionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话跟踪：每条入站事件都先调 {@link #attachSessionId(String, String, String)} 拿到当前
 * session id。逻辑：
 *
 * <ul>
 *   <li>查 (userId, groupId) 维度最新的 ai_memory_session</li>
 *   <li>如果 ended_at 已标 → 创建新 session</li>
 *   <li>如果 started_at / 最近一条事件距今 > gapMinutes → 创建新 session</li>
 *   <li>否则复用，并把 message_count++</li>
 * </ul>
 *
 * <p>另起一个 @Scheduled 任务每分钟扫一遍未结束的 session，超时则标 ended_at，让
 * MemoryCompiler 接管蒸馏。</p>
 */
@Slf4j
@Component
public class SessionTracker {

    @Autowired
    private IAiMemorySessionService sessionService;

    @Value("${aiAgent.memory.sessionGapMinutes:30}")
    private int sessionGapMinutes;

    /**
     * 给一次入站消息分配 session_id（复用现有的活跃 session 或开新）。
     */
    public synchronized String attachSessionId(String userId, String groupId, String platform) {
        if (StringUtils.isBlank(userId)) {
            userId = "_anonymous";
        }
        // 找该 (user, group) 最近一条 session
        // groupId 为空必须用 IS NULL 约束，否则私聊消息会复用到同一用户最近的【群】session 造成串味。
        // 注意不能写成 eq(groupId=null)：MyBatis-Plus 会生成 `=NULL` 永假。
        AiMemorySession latest = sessionService.getOne(new LambdaQueryWrapper<AiMemorySession>()
                .eq(AiMemorySession::getUserId, userId)
                .eq(StringUtils.isNotBlank(groupId), AiMemorySession::getGroupId, groupId)
                .isNull(StringUtils.isBlank(groupId), AiMemorySession::getGroupId)
                .orderByDesc(AiMemorySession::getStartedAt)
                .last("limit 1"));

        LocalDateTime now = LocalDateTime.now();
        // 以"最近一条事件"为基准判活跃：last_event_at 缺失时回退 started_at（兼容回填前的老行）
        LocalDateTime lastActive = latest == null ? null
                : (latest.getLastEventAt() != null ? latest.getLastEventAt() : latest.getStartedAt());
        boolean reuse = latest != null
                && latest.getEndedAt() == null
                && lastActive != null
                && lastActive.plusMinutes(sessionGapMinutes).isAfter(now);

        if (reuse) {
            latest.setMessageCount((latest.getMessageCount() == null ? 0 : latest.getMessageCount()) + 1);
            latest.setLastEventAt(now);
            sessionService.updateById(latest);
            return latest.getSessionId();
        }
        // 新建
        AiMemorySession fresh = new AiMemorySession();
        fresh.setSessionId(generateSessionId());
        fresh.setUserId(userId);
        fresh.setGroupId(groupId);
        fresh.setPlatform(platform);
        fresh.setStartedAt(now);
        fresh.setLastEventAt(now);
        fresh.setMessageCount(1);
        sessionService.save(fresh);
        log.info("SessionTracker 新开 session={} user={} group={}", fresh.getSessionId(), userId, groupId);
        return fresh.getSessionId();
    }

    /**
     * 扫描所有 ended_at 为 null 的 session，最近一条事件超过 gapMinutes 就标 ended_at。
     * MemoryCompiler 会订阅这个状态变化来跑 LLM 蒸馏。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void sweepInactiveSessions() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(sessionGapMinutes);
            // 查所有未结束的、最近一条事件早于 threshold 的 session（last_event_at 回填后等价于"静默超 gap"）
            List<AiMemorySession> stale = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                    .isNull(AiMemorySession::getEndedAt)
                    .lt(AiMemorySession::getLastEventAt, threshold)
                    .last("limit 50"));
            for (AiMemorySession s : stale) {
                s.setEndedAt(LocalDateTime.now());
                sessionService.updateById(s);
                log.info("SessionTracker 标记 session={} 结束 user={} duration_min={}",
                        s.getSessionId(), s.getUserId(),
                        java.time.Duration.between(s.getStartedAt(), s.getEndedAt()).toMinutes());
            }
        } catch (Exception e) {
            log.warn("SessionTracker sweep 异常", e);
        }
    }

    /** Owner 强制 reset：给 (userId, groupId) 现有未结束 session 立即标 ended。 */
    public synchronized int forceEndCurrent(String userId, String groupId) {
        List<AiMemorySession> open = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                .eq(AiMemorySession::getUserId, userId)
                .eq(StringUtils.isNotBlank(groupId), AiMemorySession::getGroupId, groupId)
                .isNull(StringUtils.isBlank(groupId), AiMemorySession::getGroupId)
                .isNull(AiMemorySession::getEndedAt));
        int n = 0;
        for (AiMemorySession s : open) {
            s.setEndedAt(LocalDateTime.now());
            sessionService.updateById(s);
            n++;
        }
        return n;
    }

    private String generateSessionId() {
        return "s-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
