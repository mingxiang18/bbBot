package com.bb.bot.api;

import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.api.qq.QqToBbMessageApi;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BbMessageApiImplTest {

    private QqToBbMessageApi qqToBbMessageApi;
    private MemoryEventRecorder memoryEventRecorder;
    private BbMessageApiImpl api;

    @BeforeEach
    void setUp() {
        qqToBbMessageApi = mock(QqToBbMessageApi.class);
        memoryEventRecorder = mock(MemoryEventRecorder.class);
        api = new BbMessageApiImpl();
        ReflectionTestUtils.setField(api, "qqToBbMessageApi", qqToBbMessageApi);
        ReflectionTestUtils.setField(api, "memoryEventRecorder", memoryEventRecorder);
    }

    @Test
    void sendMessage_recordsOutboundAfterSuccessfulPlatformSend() {
        BbSendMessage out = new BbSendMessage();
        out.setBotType(BotType.QQ);
        out.setMessageList(List.of(BbMessageContent.buildTextContent("可见回复")));

        api.sendMessage(out);

        verify(qqToBbMessageApi).sendMessage(out);
        verify(memoryEventRecorder).recordOutbound(out, "handler_reply");
    }
}
