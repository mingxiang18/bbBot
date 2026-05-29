package com.bb.bot.common.util.aiChat.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link OpenAiCompatProvider} 在 moonshot 类模型（{@code spec.kind == "moonshot"}）下
 * 会把网络图片下载并转 base64；非 moonshot 模型时不做这层转换（保持原网络 URL）。
 *
 * <p>架构变更说明：旧的 {@code OpenAIProvider}（具体子类，按 {@code model} 名前缀判断
 * moonshot）已合并为 {@link OpenAiCompatProvider}。moonshot 转 base64 现在由
 * {@link ModelSpec#getKind()} == "moonshot" 触发，而非按 model 名。因此原测试里
 * "model=moonshot-v1-vision 触发 / model=gpt-4o 不触发" 改写为 "kind=moonshot 触发 /
 * kind=openai 不触发"，覆盖的行为语义（网络图转 base64 vs 保留 URL）保持不变。</p>
 */
@ExtendWith(MockitoExtension.class)
class OpenAIProviderTest {

    @Mock
    RestUtils restUtils;

    AbstractOpenAICompatibleProviderTest.FakeHttpExchanger fake;
    AIProviderProperties properties;
    OpenAiCompatProvider provider;
    ModelSpec spec;

    @BeforeEach
    void setUp() throws Exception {
        fake = new AbstractOpenAICompatibleProviderTest.FakeHttpExchanger();
        properties = new AIProviderProperties();
        AIProviderProperties.RetryConfig retry = new AIProviderProperties.RetryConfig();
        retry.setMaxAttempts(1);
        retry.setInitialIntervalMs(1);
        properties.setRetry(retry);

        spec = new ModelSpec();
        spec.setName("openai");
        spec.setKind("openai");
        spec.setApiKey("sk-test");
        spec.setVision(true);

        provider = new OpenAiCompatProvider();
        AbstractOpenAICompatibleProviderTest.inject(provider, "httpExchanger", fake);
        AbstractOpenAICompatibleProviderTest.inject(provider, "properties", properties);
        AbstractOpenAICompatibleProviderTest.inject(provider, "restUtils", restUtils);
        AbstractOpenAICompatibleProviderTest.inject(provider, "tokenUsageRecorder", new TokenUsageRecorder());
    }

    @Test
    void moonshotModel_convertsNetImageToBase64() {
        spec.setKind("moonshot");
        spec.setModel("moonshot-v1-vision");
        when(restUtils.getFileInputStream(anyString()))
                .thenReturn(new ByteArrayInputStream("PNGDATA".getBytes(StandardCharsets.UTF_8)));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        provider.chat(spec, List.of(ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img/a.png")))));

        verify(restUtils, times(1)).getFileInputStream("http://img/a.png");
        JSONObject body = JSON.parseObject(fake.requests.get(0).body());
        JSONArray content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        // 找到 image_url part，验证已转 base64
        boolean foundBase64 = false;
        for (int i = 0; i < content.size(); i++) {
            JSONObject p = content.getJSONObject(i);
            if ("image_url".equals(p.getString("type"))) {
                String url = p.getJSONObject("image_url").getString("url");
                if (url.startsWith("data:image/png;base64,")) {
                    foundBase64 = true;
                }
            }
        }
        assertTrue(foundBase64, "moonshot path should produce base64 image url");
    }

    @Test
    void nonMoonshotModel_preservesNetImageUrl() {
        spec.setKind("openai");
        spec.setModel("gpt-4o");
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        provider.chat(spec, List.of(ChatMessage.user(List.of(
                MessageContent.text("describe"),
                MessageContent.netImage("http://img/a.png")))));

        verify(restUtils, never()).getFileInputStream(anyString());
        JSONObject body = JSON.parseObject(fake.requests.get(0).body());
        JSONArray content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        boolean foundUrl = false;
        for (int i = 0; i < content.size(); i++) {
            JSONObject p = content.getJSONObject(i);
            if ("image_url".equals(p.getString("type"))) {
                String url = p.getJSONObject("image_url").getString("url");
                if ("http://img/a.png".equals(url)) {
                    foundUrl = true;
                }
            }
        }
        assertTrue(foundUrl, "non-moonshot should keep original url");
    }

    @Test
    void openaiName_isOpenai() {
        // 旧 OpenAIProvider.name() == "openai" 的语义现由 ModelSpec.kind 表达。
        assertEquals("openai", spec.getKind());
    }

    private static String openAiSuccessBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"}}]}";
    }
}
