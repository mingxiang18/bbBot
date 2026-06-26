package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryEventRecorderTest {

    private IAiMemoryEventService eventService;
    private SessionTracker sessionTracker;
    private MemoryEventRecorder recorder;

    @BeforeEach
    void setUp() {
        eventService = mock(IAiMemoryEventService.class);
        sessionTracker = mock(SessionTracker.class);
        recorder = new MemoryEventRecorder();
        ReflectionTestUtils.setField(recorder, "eventService", eventService);
        ReflectionTestUtils.setField(recorder, "sessionTracker", sessionTracker);
        when(sessionTracker.attachSessionId(anyString(), anyString(), anyString())).thenReturn("s1");
    }

    @Test
    void recordOutbound_fromSendMessage_recordsVisibleBotReply() {
        BbSendMessage out = sendMessage(List.of(
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent("星露谷答案")));

        recorder.recordOutbound(out, "handler_reply");

        AiMemoryEvent saved = captureSaved();
        assertThat(saved.getSessionId()).isEqualTo("s1");
        assertThat(saved.getPlatform()).isEqualTo("qq");
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getGroupId()).isEqualTo("g1");
        assertThat(saved.getSource()).isEqualTo("bot");
        assertThat(saved.getKind()).isEqualTo("handler_reply");
        assertThat(saved.getText()).isEqualTo("@u1 星露谷答案");
        assertThat(JSON.parseArray(saved.getPayload(), BbMessageContent.class))
                .extracting(BbMessageContent::getType)
                .containsExactly(BbSendMessageType.AT, BbSendMessageType.TEXT);
    }

    @Test
    void withOutboundKind_overridesNextOutboundKind() {
        BbSendMessage out = sendMessage(List.of(BbMessageContent.buildTextContent("AI答案")));

        recorder.withOutboundKind("chat_reply", () -> recorder.recordOutbound(out, "handler_reply"));

        assertThat(captureSaved().getKind()).isEqualTo("chat_reply");
    }

    private BbSendMessage sendMessage(List<BbMessageContent> contents) {
        BbSendMessage out = new BbSendMessage();
        out.setBotType("qq");
        out.setMessageType("group");
        out.setUserId("u1");
        out.setGroupId("g1");
        out.setReceiveMessageId("m1");
        out.setMessageList(contents);
        return out;
    }

    private AiMemoryEvent captureSaved() {
        ArgumentCaptor<AiMemoryEvent> captor = ArgumentCaptor.forClass(AiMemoryEvent.class);
        verify(eventService).save(captor.capture());
        return captor.getValue();
    }
}
