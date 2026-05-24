package com.bb.bot.common.util.aiChat.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 覆盖 {@link AbstractOpenAICompatibleProvider} 的核心行为：
 * <ul>
 *   <li>状态码到 ErrorType 的映射（401/403/429/5xx/4xx）</li>
 *   <li>retry 仅对可重试错误生效，4xx 不重试</li>
 *   <li>vision 关闭时图像 part 被剥离</li>
 *   <li>未配置 apiKey 抛 UNAUTHORIZED</li>
 *   <li>响应 choices 缺失抛 FATAL</li>
 * </ul>
 *
 * 使用 DeepSeekProvider 作为具体实现（无特殊预处理，纯 OpenAI 兼容协议）。
 */
class AbstractOpenAICompatibleProviderTest {

    private FakeHttpExchanger fake;
    private AIProviderProperties properties;
    private DeepSeekProvider provider;

    @BeforeEach
    void setUp() {
        fake = new FakeHttpExchanger();
        properties = new AIProviderProperties();
        AIProviderProperties.RetryConfig retry = new AIProviderProperties.RetryConfig();
        retry.setMaxAttempts(3);
        retry.setInitialIntervalMs(1);
        retry.setMultiplier(1.0);
        retry.setMaxIntervalMs(1);
        properties.setRetry(retry);

        AIProviderProperties.ProviderConfig cfg = new AIProviderProperties.ProviderConfig();
        cfg.setApiKey("sk-test");
        cfg.setVisionEnable(false);
        properties.setDeepseek(cfg);

        provider = new DeepSeekProvider();
        provider.httpExchanger = fake;
        provider.properties = properties;
        provider.restUtils = null;
    }

    @Test
    void chat_returnsContentOnSuccess() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("hello world")));

        String result = provider.chat(List.of(ChatMessage.user("hi")));

        assertEquals("hello world", result);
        assertEquals(1, fake.requests.size());
        // 默认 baseUrl + /chat/completions
        assertEquals("https://api.deepseek.com/v1/chat/completions", fake.requests.get(0).url());
        assertEquals("Bearer sk-test", fake.requests.get(0).headers().get("Authorization"));
    }

    @Test
    void chat_throwsUnauthorizedWhenApiKeyBlank() {
        properties.getDeepseek().setApiKey(" ");
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(0, fake.requests.size(), "should not call HTTP when key missing");
    }

    @Test
    void chat_throws401AsUnauthorizedAndDoesNotRetry() {
        fake.responses.add(new HttpExchanger.HttpResponse(401, "{\"error\":\"bad key\"}"));

        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(401, ex.getHttpStatus());
        assertEquals(1, fake.requests.size(), "401 should not retry");
    }

    @Test
    void chat_throws429AsRateLimitedAndRetries() {
        fake.responses.add(new HttpExchanger.HttpResponse(429, "{}"));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        String result = provider.chat(List.of(ChatMessage.user("hi")));
        assertEquals("ok", result);
        assertEquals(2, fake.requests.size());
    }

    @Test
    void chat_throws500AsRetryableAndExhaustsAttempts() {
        for (int i = 0; i < 3; i++) {
            fake.responses.add(new HttpExchanger.HttpResponse(503, "down"));
        }
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.RETRYABLE, ex.getErrorType());
        assertEquals(503, ex.getHttpStatus());
        assertEquals(3, fake.requests.size(), "should attempt up to maxAttempts");
    }

    @Test
    void chat_throws400AsFatalAndDoesNotRetry() {
        fake.responses.add(new HttpExchanger.HttpResponse(400, "bad request"));
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.FATAL, ex.getErrorType());
        assertEquals(1, fake.requests.size(), "400 should not retry");
    }

    @Test
    void chat_ioErrorIsRetryable() {
        fake.exceptionsToThrow.add(new IOException("connection reset"));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("recovered")));

        String result = provider.chat(List.of(ChatMessage.user("hi")));
        assertEquals("recovered", result);
        assertEquals(2, fake.requests.size());
    }

    @Test
    void chat_emptyChoicesThrowsFatal() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, "{\"choices\":[]}"));
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.FATAL, ex.getErrorType());
    }

    @Test
    void chat_visionDisabledDropsImageParts() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        ChatMessage msg = ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img.png")));
        provider.chat(List.of(msg));

        assertEquals(1, fake.requests.size());
        JSONObject sentBody = JSON.parseObject(fake.requests.get(0).body());
        Object content = sentBody.getJSONArray("messages").getJSONObject(0).get("content");
        // 图片被剥掉后只剩文本，content 退化为字符串
        assertTrue(content instanceof String, "content should be plain string after image removal");
    }

    @Test
    void chat_visionEnabledKeepsImageParts() {
        properties.getDeepseek().setVisionEnable(true);
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        ChatMessage msg = ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img.png")));
        provider.chat(List.of(msg));

        JSONObject sentBody = JSON.parseObject(fake.requests.get(0).body());
        Object content = sentBody.getJSONArray("messages").getJSONObject(0).get("content");
        assertFalse(content instanceof String, "content should remain array when vision enabled");
    }

    @Test
    void chat_serializesSystemRoleWithStringContent() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        provider.chat(List.of(ChatMessage.system("personality"), ChatMessage.user("hi")));

        JSONObject sent = JSON.parseObject(fake.requests.get(0).body());
        assertEquals("system", sent.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("personality", sent.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    void chat_usesConfiguredBaseUrlAndModel() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        properties.getDeepseek().setBaseUrl("http://custom.example/v9");
        properties.getDeepseek().setModel("custom-model-x");

        provider.chat(List.of(ChatMessage.user("hi")));

        assertEquals("http://custom.example/v9/chat/completions", fake.requests.get(0).url());
        JSONObject sent = JSON.parseObject(fake.requests.get(0).body());
        assertEquals("custom-model-x", sent.getString("model"));
    }

    @Test
    void isConfigured_falseWhenApiKeyBlank() {
        properties.getDeepseek().setApiKey("");
        assertFalse(provider.isConfigured());
    }

    @Test
    void deepseek_defaults_match() {
        DeepSeekProvider p = new DeepSeekProvider();
        assertEquals("deepseek", p.name());
    }

    @Test
    void retryStopsImmediatelyWhenSuccess() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        fake.responses.add(new HttpExchanger.HttpResponse(500, "should not be reached"));
        provider.chat(List.of(ChatMessage.user("hi")));
        assertEquals(1, fake.requests.size());
    }

    private static String openAiSuccessBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"}}]}";
    }

    /** 简单的内存 HTTP 假实现，便于覆盖各种状态/异常路径。 */
    static class FakeHttpExchanger implements HttpExchanger {
        final List<Recorded> requests = new ArrayList<>();
        final List<HttpResponse> responses = new ArrayList<>();
        final List<Exception> exceptionsToThrow = new ArrayList<>();
        private final AtomicInteger callIndex = new AtomicInteger();

        @Override
        public HttpResponse post(String url, Map<String, String> headers, String jsonBody) throws Exception {
            requests.add(new Recorded(url, Map.copyOf(headers), jsonBody));
            int idx = callIndex.getAndIncrement();
            if (idx < exceptionsToThrow.size()) {
                throw exceptionsToThrow.get(idx);
            }
            int respIdx = idx - exceptionsToThrow.size();
            if (respIdx < responses.size()) {
                return responses.get(respIdx);
            }
            throw new IllegalStateException("FakeHttpExchanger ran out of canned responses at call " + idx);
        }

        record Recorded(String url, Map<String, String> headers, String body) {}
    }
}
