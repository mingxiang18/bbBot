package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryQueryServiceTest {

    private IAiMemoryEventService eventService;
    private SessionTracker sessionTracker;
    private MemoryQueryService queryService;

    @BeforeEach
    void setUp() {
        eventService = mock(IAiMemoryEventService.class);
        sessionTracker = mock(SessionTracker.class);
        queryService = new MemoryQueryService();
        ReflectionTestUtils.setField(queryService, "eventService", eventService);
        ReflectionTestUtils.setField(queryService, "sessionTracker", sessionTracker);
        when(sessionTracker.attachSessionId("u1", "g1", "qq")).thenReturn("s1");
    }

    @Test
    void loadChatContext_readsVisibleUserAndBotEventsRegardlessOfKind_andSortsAscending() {
        AiMemoryEvent newerCommandReply = event("bot", "handler_reply", LocalDateTime.parse("2026-06-26T10:01:00"));
        AiMemoryEvent olderCommand = event("user", "command", LocalDateTime.parse("2026-06-26T10:00:00"));
        when(eventService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(newerCommandReply, olderCommand));

        List<AiMemoryEvent> out = queryService.loadChatContext("u1", "g1", "qq", 20);

        assertThat(out).containsExactly(olderCommand, newerCommandReply);
        captureWrapper();
        verify(sessionTracker).attachSessionId("u1", "g1", "qq");
    }

    private AiMemoryEvent event(String source, String kind, LocalDateTime createdAt) {
        AiMemoryEvent e = new AiMemoryEvent();
        e.setSource(source);
        e.setKind(kind);
        e.setCreatedAt(createdAt);
        return e;
    }

    private LambdaQueryWrapper<AiMemoryEvent> captureWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<AiMemoryEvent>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(eventService).list(captor.capture());
        return captor.getValue();
    }
}
