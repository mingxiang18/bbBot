package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.aiAgent.memory.MemoryCompiler;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.aiAgent.memory.SessionTracker;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link BbAiMemoryHandler} 的回复路径已迁移到 {@link BbReplies}，
 * 且各决策分支（owner 校验 / 空结果 / 用法提示 / 截断等）的回复文本与重构前一致（行为等价）。
 */
class BbAiMemoryHandlerTest {

    private BbReplies replies;
    private AiAgentAuthService authService;
    private MemoryQueryService memoryQueryService;
    private SessionTracker sessionTracker;
    private MemoryCompiler memoryCompiler;
    private BbAiMemoryHandler handler;

    @BeforeEach
    void setUp() {
        replies = mock(BbReplies.class);
        authService = mock(AiAgentAuthService.class);
        memoryQueryService = mock(MemoryQueryService.class);
        sessionTracker = mock(SessionTracker.class);
        memoryCompiler = mock(MemoryCompiler.class);

        handler = new BbAiMemoryHandler();
        ReflectionTestUtils.setField(handler, "replies", replies);
        ReflectionTestUtils.setField(handler, "authService", authService);
        ReflectionTestUtils.setField(handler, "memoryQueryService", memoryQueryService);
        ReflectionTestUtils.setField(handler, "sessionTracker", sessionTracker);
        ReflectionTestUtils.setField(handler, "memoryCompiler", memoryCompiler);
    }

    private BbReceiveMessage msg(String text) {
        BbReceiveMessage src = new BbReceiveMessage();
        src.setBotType("qq");
        src.setMessageType("group");
        src.setUserId("u-123");
        src.setGroupId("g-456");
        src.setMessageId("m-789");
        src.setMessage(text);
        src.setMessageContentList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        return src;
    }

    private AiMemoryEvent event(String kind, String userId, String text) {
        AiMemoryEvent e = new AiMemoryEvent();
        e.setKind(kind);
        e.setUserId(userId);
        e.setText(text);
        e.setCreatedAt(LocalDateTime.of(2026, 5, 29, 12, 0));
        return e;
    }

    private void owner() {
        when(authService.isOwner("u-123")).thenReturn(true);
    }

    // ---- owner 校验 ----

    @Test
    void nonOwner_should_reply_no_permission_and_skip_business() {
        when(authService.isOwner(any())).thenReturn(false);

        handler.tail(msg("/aiAgent.memory.tail"));

        verify(replies).text(any(BbReceiveMessage.class), eq("无权限（仅 owner 可执行）"));
        verifyNoInteractions(memoryQueryService);
    }

    // ---- tail ----

    @Test
    void tail_empty_should_reply_empty_stream() {
        owner();
        when(memoryQueryService.tail(anyInt())).thenReturn(Collections.emptyList());

        handler.tail(msg("/aiAgent.memory.tail"));

        verify(memoryQueryService).tail(20);
        verify(replies).text(any(BbReceiveMessage.class), eq("事件流为空"));
    }

    @Test
    void tail_with_explicit_n_clamped_and_listed() {
        owner();
        when(memoryQueryService.tail(anyInt()))
                .thenReturn(List.of(event("user", "u-1", "hello")));

        handler.tail(msg("/aiAgent.memory.tail 500"));

        // n 被 clamp 到上限 100
        verify(memoryQueryService).tail(100);
        verify(replies).text(any(BbReceiveMessage.class),
                eq("最近 1 条事件：\n[2026-05-29T12:00] user / u-1 / hello"));
    }

    @Test
    void tail_blank_userId_rendered_as_dash() {
        owner();
        when(memoryQueryService.tail(anyInt()))
                .thenReturn(List.of(event("sys", "  ", "x")));

        handler.tail(msg("/aiAgent.memory.tail 0")); // 0 被 clamp 到下限 1

        verify(memoryQueryService).tail(1);
        verify(replies).text(any(BbReceiveMessage.class),
                eq("最近 1 条事件：\n[2026-05-29T12:00] sys / - / x"));
    }

    // ---- search ----

    @Test
    void search_no_keyword_should_reply_usage() {
        owner();

        handler.search(msg("/aiAgent.memory.search"));

        verify(replies).text(any(BbReceiveMessage.class), eq("用法: /aiAgent.memory.search <关键字>"));
        verifyNoInteractions(memoryQueryService);
    }

    @Test
    void search_no_hit_should_reply_not_found() {
        owner();
        when(memoryQueryService.searchText(eq("foo"), anyInt())).thenReturn(Collections.emptyList());

        handler.search(msg("/aiAgent.memory.search foo"));

        verify(replies).text(any(BbReceiveMessage.class), eq("没找到包含 \"foo\" 的事件"));
    }

    @Test
    void search_hit_should_list_events() {
        owner();
        when(memoryQueryService.searchText(eq("kw"), anyInt()))
                .thenReturn(List.of(event("user", "u-1", "matched line")));

        handler.search(msg("/aiAgent.memory.search kw"));

        verify(replies).text(any(BbReceiveMessage.class),
                eq("命中 1 条:\n- [2026-05-29T12:00] user: matched line"));
    }

    // ---- session ----

    @Test
    void session_should_reply_session_id() {
        owner();
        when(sessionTracker.attachSessionId("u-123", "g-456", "qq")).thenReturn("sess-1");

        handler.session(msg("/aiAgent.memory.session"));

        verify(replies).text(any(BbReceiveMessage.class), eq("当前 session_id = sess-1"));
    }

    // ---- reset ----

    @Test
    void reset_should_reply_ended_count() {
        owner();
        when(sessionTracker.forceEndCurrent("u-123", "g-456")).thenReturn(2);

        handler.reset(msg("/aiAgent.memory.reset"));

        verify(replies).text(any(BbReceiveMessage.class),
                eq("已强制结束 2 个 session；下一条消息将进入新 session（蒸馏会异步进行）"));
    }

    // ---- rebuild ----

    @Test
    void rebuild_should_reply_length() {
        owner();
        when(memoryCompiler.rebuildAll("u-123")).thenReturn("abcde");

        handler.rebuild(msg("/aiAgent.memory.rebuild"));

        verify(replies).text(any(BbReceiveMessage.class), eq("已强制重编 memory.md，长度 5 字符"));
    }

    @Test
    void rebuild_null_should_reply_zero_length() {
        owner();
        when(memoryCompiler.rebuildAll("u-123")).thenReturn(null);

        handler.rebuild(msg("/aiAgent.memory.rebuild"));

        verify(replies).text(any(BbReceiveMessage.class), eq("已强制重编 memory.md，长度 0 字符"));
    }

    // ---- view ----

    @Test
    void view_blank_should_reply_no_memory() {
        owner();
        when(memoryCompiler.ensureCompiledMemory("u-123")).thenReturn("   ");

        handler.view(msg("/aiAgent.memory.view"));

        verify(replies).text(any(BbReceiveMessage.class), eq("（暂无记忆）"));
    }

    @Test
    void view_short_should_reply_as_is() {
        owner();
        when(memoryCompiler.ensureCompiledMemory("u-123")).thenReturn("short mem");

        handler.view(msg("/aiAgent.memory.view"));

        verify(replies).text(any(BbReceiveMessage.class), eq("short mem"));
    }

    @Test
    void view_long_should_truncate_to_4kb() {
        owner();
        String big = "a".repeat(5000);
        when(memoryCompiler.ensureCompiledMemory("u-123")).thenReturn(big);

        handler.view(msg("/aiAgent.memory.view"));

        verify(replies).text(any(BbReceiveMessage.class),
                eq(big.substring(0, 4000) + "\n...(truncated)"));
    }
}
