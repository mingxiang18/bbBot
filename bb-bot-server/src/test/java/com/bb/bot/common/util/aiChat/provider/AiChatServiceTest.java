package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证 {@link AiChatService} 选取激活 provider 的逻辑。
 */
class AiChatServiceTest {

    @Test
    void picksProviderByActiveName() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("openai");

        StubProvider openai = new StubProvider("openai", true, "from-openai");
        StubProvider deepseek = new StubProvider("deepseek", true, "from-deepseek");

        AiChatService service = new AiChatService(List.of(openai, deepseek), props);
        String result = service.chat(List.of(ChatMessage.user("hi")));

        assertEquals("from-openai", result);
        assertEquals(1, openai.calls);
        assertEquals(0, deepseek.calls);
    }

    @Test
    void switchActiveProviderTakesEffect() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("deepseek");

        StubProvider openai = new StubProvider("openai", true, "from-openai");
        StubProvider deepseek = new StubProvider("deepseek", true, "from-deepseek");

        AiChatService service = new AiChatService(List.of(openai, deepseek), props);
        assertEquals("from-deepseek", service.chat(List.of(ChatMessage.user("hi"))));
    }

    @Test
    void returnsNullWhenProviderNameUnknown() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("doesNotExist");

        AiChatService service = new AiChatService(List.of(new StubProvider("openai", true, "x")), props);
        assertNull(service.chat(List.of(ChatMessage.user("hi"))));
        assertFalse(service.isConfigured());
    }

    @Test
    void returnsNullWhenProviderNotConfigured() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("openai");

        StubProvider openai = new StubProvider("openai", false, "ignored");
        AiChatService service = new AiChatService(List.of(openai), props);

        assertNull(service.chat(List.of(ChatMessage.user("hi"))));
        assertFalse(service.isConfigured());
        assertEquals(0, openai.calls);
    }

    @Test
    void propagatesProviderException() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("openai");
        StubProvider boom = new StubProvider("openai", true, null) {
            @Override
            public String chat(List<ChatMessage> messages) {
                calls++;
                throw new AIException(AIException.ErrorType.RATE_LIMITED, 429, "slow down");
            }
        };
        AiChatService service = new AiChatService(List.of(boom), props);

        AIException ex = assertThrows(AIException.class,
                () -> service.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.RATE_LIMITED, ex.getErrorType());
    }

    @Test
    void currentReturnsActiveBean() {
        AIProviderProperties props = new AIProviderProperties();
        props.setActiveProvider("openai");
        StubProvider openai = new StubProvider("openai", true, "x");
        AiChatService service = new AiChatService(List.of(openai), props);
        assertTrue(service.current() == openai);
    }

    static class StubProvider implements AIProvider {
        final String name;
        final boolean configured;
        final String reply;
        int calls = 0;

        StubProvider(String name, boolean configured, String reply) {
            this.name = name;
            this.configured = configured;
            this.reply = reply;
        }

        @Override
        public String chat(List<ChatMessage> messages) {
            calls++;
            return reply;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
