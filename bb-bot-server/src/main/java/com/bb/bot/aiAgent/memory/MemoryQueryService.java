package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 给上层 handler 的查询门面。按 session / 用户 / 群组维度拉 ai_memory_event。
 *
 * <p>默认聊天上下文读取同 session 里用户和机器人真实出现在聊天窗口的近邻事件；
 * kind 保留用于审计、管理和后台查询，不再作为普通聊天上下文的过滤条件。</p>
 */
@Slf4j
@Component
public class MemoryQueryService {

    @Autowired
    private IAiMemoryEventService eventService;

    @Autowired
    private SessionTracker sessionTracker;

    /** 当前 session 内、按时间正序的最近 N 条可见对话事件。BbAiChatHandler 用。 */
    public List<AiMemoryEvent> loadChatContext(String userId, String groupId, String platform, int limit) {
        return loadVisibleContext(userId, groupId, platform, limit);
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
                .eq(StringUtils.isNotBlank(platform), AiMemoryEvent::getPlatform, platform)
                .eq(StringUtils.isNotBlank(sessionId), AiMemoryEvent::getSessionId, sessionId)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + safeLimit(limit));
        List<AiMemoryEvent> rows = new ArrayList<>(eventService.list(wrap));
        rows.sort(Comparator.comparing(AiMemoryEvent::getCreatedAt));
        return rows;
    }

    private List<AiMemoryEvent> loadVisibleContext(String userId, String groupId, String platform, int limit) {
        if (StringUtils.isBlank(userId)) return Collections.emptyList();
        String sessionId;
        try {
            sessionId = sessionTracker.attachSessionId(userId, groupId, platform);
        } catch (Exception e) {
            log.warn("loadVisibleContext 获取 session_id 失败，回退到无 session 限制", e);
            sessionId = null;
        }
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .in(AiMemoryEvent::getSource, Arrays.asList("user", "bot"))
                .eq(AiMemoryEvent::getUserId, userId)
                .eq(StringUtils.isNotBlank(groupId), AiMemoryEvent::getGroupId, groupId)
                .isNull(StringUtils.isBlank(groupId), AiMemoryEvent::getGroupId)
                .eq(StringUtils.isNotBlank(platform), AiMemoryEvent::getPlatform, platform)
                .eq(StringUtils.isNotBlank(sessionId), AiMemoryEvent::getSessionId, sessionId)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + safeLimit(limit));
        List<AiMemoryEvent> rows = new ArrayList<>(eventService.list(wrap));
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
                .last("limit " + safeLimit(limit));
        List<AiMemoryEvent> rows = new ArrayList<>(eventService.list(wrap));
        rows.sort(Comparator.comparing(AiMemoryEvent::getCreatedAt));
        return rows;
    }

    /** 给 owner 的 tail 命令用。 */
    public List<AiMemoryEvent> tail(int limit) {
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + safeLimit(limit));
        return eventService.list(wrap);
    }

    /** 给 owner 的 search 命令用：text LIKE %query%。 */
    public List<AiMemoryEvent> searchText(String query, int limit) {
        LambdaQueryWrapper<AiMemoryEvent> wrap = new LambdaQueryWrapper<AiMemoryEvent>()
                .like(AiMemoryEvent::getText, query)
                .orderByDesc(AiMemoryEvent::getCreatedAt)
                .last("limit " + safeLimit(limit));
        return eventService.list(wrap);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }
}
