package com.bb.bot.common.util.aiChat.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * 覆盖 {@link OpenAiCompatProvider} 的核心行为：
 * <ul>
 *   <li>状态码到 ErrorType 的映射（401/403/429/5xx/4xx）</li>
 *   <li>retry 仅对可重试错误生效，4xx 不重试，首次成功立即停止</li>
 *   <li>vision 关闭时图像 part 被剥离，开启时保留</li>
 *   <li>未配置 apiKey 抛 UNAUTHORIZED 且不发 HTTP</li>
 *   <li>响应 choices 缺失抛 FATAL</li>
 *   <li>baseUrl / model 由 {@link ModelSpec} 决定</li>
 * </ul>
 *
 * <p>架构变更说明：旧的 {@code DeepSeekProvider}（{@code AbstractOpenAICompatibleProvider}
 * 的具体子类，配置来自 {@code AIProviderProperties.ProviderConfig}）已合并为单个
 * {@link OpenAiCompatProvider}，每次调用以 {@link ModelSpec} 携带 baseUrl/apiKey/model/kind/vision。
 * 因此原先针对 {@code provider.chat(messages)} 的断言改写为 {@code provider.chat(spec, messages)}。</p>
 */
class AbstractOpenAICompatibleProviderTest {

    private FakeHttpExchanger fake;
    private AIProviderProperties properties;
    private OpenAiCompatProvider provider;
    private ModelSpec spec;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeHttpExchanger();
        properties = new AIProviderProperties();
        AIProviderProperties.RetryConfig retry = new AIProviderProperties.RetryConfig();
        retry.setMaxAttempts(3);
        retry.setInitialIntervalMs(1);
        retry.setMultiplier(1.0);
        retry.setMaxIntervalMs(1);
        properties.setRetry(retry);

        // 旧 DeepSeekProvider 的默认行为：deepseek baseUrl + 非视觉。新架构由 ModelSpec 表达。
        spec = new ModelSpec();
        spec.setName("deepseek");
        spec.setKind("deepseek");
        spec.setBaseUrl("https://api.deepseek.com/v1");
        spec.setApiKey("sk-test");
        spec.setModel("deepseek-chat");
        spec.setVision(false);

        provider = new OpenAiCompatProvider();
        inject(provider, "httpExchanger", fake);
        inject(provider, "properties", properties);
        inject(provider, "restUtils", null);
        // success body 不带 usage，recordUsage 会直接 return，不触碰 recorder；仍注入一个实例避免意外 NPE。
        inject(provider, "tokenUsageRecorder", new TokenUsageRecorder());
    }

    @Test
    void chat_returnsContentOnSuccess() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("hello world")));

        String result = provider.chat(spec, List.of(ChatMessage.user("hi")));

        assertEquals("hello world", result);
        assertEquals(1, fake.requests.size());
        // baseUrl + /chat/completions
        assertEquals("https://api.deepseek.com/v1/chat/completions", fake.requests.get(0).url());
        assertEquals("Bearer sk-test", fake.requests.get(0).headers().get("Authorization"));
    }

    @Test
    void chat_throwsUnauthorizedWhenApiKeyBlank() {
        spec.setApiKey(" ");
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(spec, List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(0, fake.requests.size(), "should not call HTTP when key missing");
    }

    @Test
    void chat_throws401AsUnauthorizedAndDoesNotRetry() {
        fake.responses.add(new HttpExchanger.HttpResponse(401, "{\"error\":\"bad key\"}"));

        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(spec, List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.UNAUTHORIZED, ex.getErrorType());
        assertEquals(401, ex.getHttpStatus());
        assertEquals(1, fake.requests.size(), "401 should not retry");
    }

    @Test
    void chat_throws429AsRateLimitedAndRetries() {
        fake.responses.add(new HttpExchanger.HttpResponse(429, "{}"));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        String result = provider.chat(spec, List.of(ChatMessage.user("hi")));
        assertEquals("ok", result);
        assertEquals(2, fake.requests.size());
    }

    @Test
    void chat_throws500AsRetryableAndExhaustsAttempts() {
        for (int i = 0; i < 3; i++) {
            fake.responses.add(new HttpExchanger.HttpResponse(503, "down"));
        }
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(spec, List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.RETRYABLE, ex.getErrorType());
        assertEquals(503, ex.getHttpStatus());
        assertEquals(3, fake.requests.size(), "should attempt up to maxAttempts");
    }

    @Test
    void chat_throws400AsFatalAndDoesNotRetry() {
        fake.responses.add(new HttpExchanger.HttpResponse(400, "bad request"));
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(spec, List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.FATAL, ex.getErrorType());
        assertEquals(1, fake.requests.size(), "400 should not retry");
    }

    @Test
    void chat_ioErrorIsRetryable() {
        fake.exceptionsToThrow.add(new IOException("connection reset"));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("recovered")));

        String result = provider.chat(spec, List.of(ChatMessage.user("hi")));
        assertEquals("recovered", result);
        assertEquals(2, fake.requests.size());
    }

    @Test
    void chat_emptyChoicesThrowsFatal() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, "{\"choices\":[]}"));
        AIException ex = assertThrows(AIException.class,
                () -> provider.chat(spec, List.of(ChatMessage.user("hi"))));
        assertSame(AIException.ErrorType.FATAL, ex.getErrorType());
    }

    @Test
    void chat_visionDisabledDropsImageParts() {
        spec.setVision(false);
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        ChatMessage msg = ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img.png")));
        provider.chat(spec, List.of(msg));

        assertEquals(1, fake.requests.size());
        JSONObject sentBody = JSON.parseObject(fake.requests.get(0).body());
        Object content = sentBody.getJSONArray("messages").getJSONObject(0).get("content");
        // 图片被剥掉后只剩文本，content 退化为字符串
        assertTrue(content instanceof String, "content should be plain string after image removal");
    }

    @Test
    void chat_visionEnabledKeepsImageParts() {
        spec.setVision(true);
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        ChatMessage msg = ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img.png")));
        provider.chat(spec, List.of(msg));

        JSONObject sentBody = JSON.parseObject(fake.requests.get(0).body());
        Object content = sentBody.getJSONArray("messages").getJSONObject(0).get("content");
        assertFalse(content instanceof String, "content should remain array when vision enabled");
    }

    @Test
    void chat_serializesSystemRoleWithStringContent() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        provider.chat(spec, List.of(ChatMessage.system("personality"), ChatMessage.user("hi")));

        JSONObject sent = JSON.parseObject(fake.requests.get(0).body());
        assertEquals("system", sent.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("personality", sent.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    void chat_usesConfiguredBaseUrlAndModel() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        spec.setBaseUrl("http://custom.example/v9");
        spec.setModel("custom-model-x");

        provider.chat(spec, List.of(ChatMessage.user("hi")));

        assertEquals("http://custom.example/v9/chat/completions", fake.requests.get(0).url());
        JSONObject sent = JSON.parseObject(fake.requests.get(0).body());
        assertEquals("custom-model-x", sent.getString("model"));
    }

    @Test
    void isConfigured_falseWhenApiKeyBlank() {
        spec.setApiKey("");
        assertFalse(spec.isConfigured());
    }

    @Test
    void deepseek_defaults_match() {
        // 旧 DeepSeekProvider.name() == "deepseek" 的语义现由 ModelSpec.kind 表达。
        assertEquals("deepseek", spec.getKind());
    }

    @Test
    void retryStopsImmediatelyWhenSuccess() {
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));
        fake.responses.add(new HttpExchanger.HttpResponse(500, "should not be reached"));
        provider.chat(spec, List.of(ChatMessage.user("hi")));
        assertEquals(1, fake.requests.size());
    }

    private static String openAiSuccessBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"}}]}";
    }

    /** 把 OpenAiCompatProvider 上 {@code @Autowired private} 字段直接塞进去（无 Spring 容器）。 */
    static void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
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
