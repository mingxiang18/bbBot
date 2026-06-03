package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 给上层 handler 的查询门面。按 kind / session / 用户 / 群组维度拉 ai_memory_event。
 *
 * <p>统一在这里实现「同 session 内最近 N 条」「按 kind 白名单过滤」「按时间窗过滤」
 * 三种常见检索，避免每个 handler 自己写。</p>
 */
@Slf4j
@Component
public class MemoryQueryService {

    @Autowired
    private IAiMemoryEventService eventService;

    @Autowired
    private SessionTracker sessionTracker;

    /** 当前 session 内、白名单 kind、按时间正序的最近 N 条。BbAiChatHandler 用。 */
    public List<AiMemoryEvent> loadChatContext(String userId, String groupId, String platform, int limit) {
        return loadContextByKinds(userId, groupId, platform, limit,
                Arrays.asList("chat", "chat_reply"));
    }

    /** Agent 模式用：包含 chat_reply + agent_reply，但不带 agent_cmd（避免 LLM 复述自己）。 */
    public List<AiMemoryEvent> loadAgentContext(String userId, String groupId, String platform, int limit) {
        return loadContextByKinds(userId, groupId, platform, limit,
                Arrays.asList("chat", "chat_reply", "agent_reply"));
    }

    private List<AiMemoryEvent> loadContextByKinds(String userId, String groupId, String platform,
                                                    int limit, List<String> kinds) {
        if (StringUtils.isBlank(userId)) return Collections.emptyList();
        String sessionId;
        try {
            sessionId = sessionTracker.attachSessionId(userId, groupId, platform);
        } catch (Exception e) {
            log.warn("loadContextByKinds 获取 session_id 失败，回退到无 session 限制", e);
            sessionId = null;
        }
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .in(AiMemoryEvent::getKind, kinds)
                .eq(AiMemoryEvent::getUserId, userId)
                .eq(StringUtils.isNotBlank(groupId), AiMemoryEvent::getGroupId, groupId)
                .isNull(StringUtils.isBlank(groupId), AiMemoryEvent::getGroupId)
                .eq(StringUtils.isNotBlank(sessionId), AiMemoryEvent::getSessionId, sessionId)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + limit);
        List<AiMemoryEvent> rows = eventService.list(wrap);
        rows.sort(Comparator.comparing(AiMemoryEvent::getCreatedAt));
        return rows;
    }

    /**
     * 跨 session 拉指定时间窗内的事件（BbChatHistoryHandler.chatHistorySummary 用，
     * 总结的范围跟原行为对齐：最近 N 条所有事件）。
     */
    public List<AiMemoryEvent> loadRecent(String userId, String groupId, int limit) {
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .eq(StringUtils.isNotBlank(groupId), AiMemoryEvent::getGroupId, groupId)
                .eq(StringUtils.isBlank(groupId), AiMemoryEvent::getUserId, userId)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + limit);
        List<AiMemoryEvent> rows = eventService.list(wrap);
        rows.sort(Comparator.comparing(AiMemoryEvent::getCreatedAt));
        return rows;
    }

    /** 给 owner 的 tail 命令用。 */
    public List<AiMemoryEvent> tail(int limit) {
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + limit);
        return eventService.list(wrap);
    }

    /** 给 owner 的 search 命令用：text LIKE %query%。 */
    public List<AiMemoryEvent> searchText(String query, int limit) {
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .like(AiMemoryEvent::getText, query)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + limit);
        return eventService.list(wrap);
    }
}
