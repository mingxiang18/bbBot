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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 OpenAIProvider 在 moonshot 模型下会把网络图片下载并转 base64；
 * 非 moonshot 模型时不做这层转换（保持原网络 URL）。
 */
@ExtendWith(MockitoExtension.class)
class OpenAIProviderTest {

    @Mock
    RestUtils restUtils;

    AbstractOpenAICompatibleProviderTest.FakeHttpExchanger fake;
    AIProviderProperties properties;
    OpenAIProvider provider;

    @BeforeEach
    void setUp() {
        fake = new AbstractOpenAICompatibleProviderTest.FakeHttpExchanger();
        properties = new AIProviderProperties();
        AIProviderProperties.RetryConfig retry = new AIProviderProperties.RetryConfig();
        retry.setMaxAttempts(1);
        retry.setInitialIntervalMs(1);
        properties.setRetry(retry);
        AIProviderProperties.ProviderConfig cfg = new AIProviderProperties.ProviderConfig();
        cfg.setApiKey("sk-test");
        cfg.setVisionEnable(true);
        properties.setOpenai(cfg);

        provider = new OpenAIProvider();
        provider.httpExchanger = fake;
        provider.properties = properties;
        provider.restUtils = restUtils;
    }

    @Test
    void moonshotModel_convertsNetImageToBase64() {
        properties.getOpenai().setModel("moonshot-v1-vision");
        when(restUtils.getFileInputStream(anyString()))
                .thenReturn(new ByteArrayInputStream("PNGDATA".getBytes(StandardCharsets.UTF_8)));
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        provider.chat(List.of(ChatMessage.user(List.of(
                com.bb.bot.common.util.aiChat.provider.MessageContent.text("describe"),
                com.bb.bot.common.util.aiChat.provider.MessageContent.netImage("http://img/a.png")))));

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
        properties.getOpenai().setModel("gpt-4o");
        fake.responses.add(new HttpExchanger.HttpResponse(200, openAiSuccessBody("ok")));

        provider.chat(List.of(ChatMessage.user(List.of(
                com.bb.bot.common.util.aiChat.provider.MessageContent.text("describe"),
                com.bb.bot.common.util.aiChat.provider.MessageContent.netImage("http://img/a.png")))));

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
        assertEquals("openai", provider.name());
    }

    private static String openAiSuccessBody(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"}}]}";
    }
}
