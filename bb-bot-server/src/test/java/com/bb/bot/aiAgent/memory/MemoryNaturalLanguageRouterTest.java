package com.bb.bot.aiAgent.memory;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.entity.bb.BbReceiveMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryNaturalLanguageRouterTest {

    private MemoryCommandService commandService;
    private BbReplies replies;
    private MemoryNaturalLanguageRouter router;

    @BeforeEach
    void setup() {
        commandService = mock(MemoryCommandService.class);
        replies = mock(BbReplies.class);
        router = new MemoryNaturalLanguageRouter();
        ReflectionTestUtils.setField(router, "commandService", commandService);
        ReflectionTestUtils.setField(router, "replies", replies);
    }

    private BbReceiveMessage msg() {
        BbReceiveMessage m = new BbReceiveMessage();
        m.setUserId("u1");
        return m;
    }

    @Test
    void remember_callsWriteExplicit() {
        when(commandService.writeExplicit(eq("u1"), anyString())).thenReturn("好的");
        assertThat(router.tryHandle(msg(), "记住：我爱喝咖啡")).isTrue();
        verify(commandService).writeExplicit("u1", "我爱喝咖啡");
    }

    @Test
    void forget_callsForget() {
        when(commandService.forget(eq("u1"), anyString())).thenReturn(1);
        assertThat(router.tryHandle(msg(), "忘掉 咖啡")).isTrue();
        verify(commandService).forget("u1", "咖啡");
    }

    @Test
    void whatRemember_callsReadableSelf() {
        when(commandService.readableSelfMemory("u1")).thenReturn("...");
        assertThat(router.tryHandle(msg(), "你还记得我什么")).isTrue();
        verify(commandService).readableSelfMemory("u1");
    }

    @Test
    void dontRecord_handledWithoutCommandService() {
        assertThat(router.tryHandle(msg(), "这个别记")).isTrue();
        verifyNoInteractions(commandService);
    }

    @Test
    void wrong_handled() {
        assertThat(router.tryHandle(msg(), "记错了")).isTrue();
        verifyNoInteractions(commandService);
    }

    @Test
    void plainChat_notHandled() {
        assertThat(router.tryHandle(msg(), "今天天气不错")).isFalse();
        verifyNoInteractions(commandService);
    }
}
