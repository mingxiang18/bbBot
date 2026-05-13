package com.bb.bot.common.util.aiChat.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completion 协议的通用实现。OpenAI、DeepSeek、Moonshot 等同协议提供商都继承它，
 * 子类只需要给出 baseUrl / apiKey / model / vision 能力。
 *
 * <p>统一处理：
 * <ul>
 *   <li>消息体序列化（含图像 part）</li>
 *   <li>vision 关闭时过滤图像</li>
 *   <li>HTTP 状态码到 {@link AIException.ErrorType} 的映射</li>
 *   <li>指数退避重试（仅对可重试错误）</li>
 * </ul>
 *
 * <p>HTTP 调用通过 {@link HttpExchanger} SPI 完成，便于在单测里替换。
 *
 * @author ren
 */
@Slf4j
public abstract class AbstractOpenAICompatibleProvider implements AIProvider {

    @Autowired
    protected HttpExchanger httpExchanger;

    @Autowired
    protected RestUtils restUtils;

    @Autowired
    protected AIProviderProperties properties;

    protected abstract AIProviderProperties.ProviderConfig config();

    /** 默认 baseUrl，用户未配置时回退。 */
    protected abstract String defaultBaseUrl();

    /** 默认 model。 */
    protected abstract String defaultModel();

    /** 子类可覆写：模型相关的图像处理。默认无操作。 */
    protected void preprocessImages(List<ChatMessage> messages) {}

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(config().getApiKey());
    }

    @Override
    public String chat(List<ChatMessage> messages) throws AIException {
        if (!isConfigured()) {
            throw new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "AI provider [" + name() + "] api key not configured");
        }

        List<ChatMessage> processed = new ArrayList<>(messages);
        preprocessImages(processed);
        if (!config().isVisionEnable()) {
            processed = stripImages(processed);
        }

        return executeWithRetry(processed);
    }

    private String executeWithRetry(List<ChatMessage> messages) {
        AIProviderProperties.RetryConfig retry = properties.getRetry();
        long interval = retry.getInitialIntervalMs();
        AIException last = null;
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            try {
                return doCall(messages);
            } catch (AIException e) {
                last = e;
                if (!e.isRetryable() || attempt == retry.getMaxAttempts()) {
                    throw e;
                }
                log.warn("AI provider [{}] call failed (attempt {}/{}, type={}, status={}): {}. Retrying in {}ms",
                        name(), attempt, retry.getMaxAttempts(), e.getErrorType(), e.getHttpStatus(), e.getMessage(), interval);
                sleep(interval);
                interval = Math.min((long) (interval * retry.getMultiplier()), retry.getMaxIntervalMs());
            }
        }
        throw last;
    }

    private String doCall(List<ChatMessage> messages) {
        String url = resolveBaseUrl() + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel());
        body.put("messages", serializeMessages(messages));
        String jsonBody = JSON.toJSONString(body);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + config().getApiKey());

        log.info("AI provider [{}] POST {} (model={}, msgs={})", name(), url, resolveModel(), messages.size());
        HttpExchanger.HttpResponse response;
        try {
            response = httpExchanger.post(url, headers, jsonBody);
        } catch (Exception e) {
            throw new AIException(AIException.ErrorType.RETRYABLE, -1,
                    "AI provider [" + name() + "] IO error: " + e.getMessage(), e);
        }

        if (response.status() / 100 == 2) {
            return parseSuccess(response.body());
        }
        throw classify(response.status(), response.body());
    }

    private String parseSuccess(String responseBody) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AIException(AIException.ErrorType.FATAL,
                        "AI provider [" + name() + "] response has no choices: " + responseBody);
            }
            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
            return content == null ? "" : content;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException(AIException.ErrorType.FATAL,
                    "AI provider [" + name() + "] response parse error: " + e.getMessage(), e);
        }
    }

    private AIException classify(int status, String body) {
        if (status == 401 || status == 403) {
            return new AIException(AIException.ErrorType.UNAUTHORIZED, status,
                    "AI provider [" + name() + "] unauthorized: " + body);
        }
        if (status == 429) {
            return new AIException(AIException.ErrorType.RATE_LIMITED, status,
                    "AI provider [" + name() + "] rate limited: " + body);
        }
        if (status >= 500) {
            return new AIException(AIException.ErrorType.RETRYABLE, status,
                    "AI provider [" + name() + "] server error: " + body);
        }
        return new AIException(AIException.ErrorType.FATAL, status,
                "AI provider [" + name() + "] client error: " + body);
    }

    private List<Map<String, Object>> serializeMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("role", roleName(m.getRole()));
            List<MessageContent> parts = m.getContents();
            if (parts.size() == 1 && parts.get(0).getType() == MessageContent.Type.TEXT) {
                obj.put("content", parts.get(0).getValue());
            } else {
                obj.put("content", serializeParts(parts));
            }
            out.add(obj);
        }
        return out;
    }

    private List<Map<String, Object>> serializeParts(List<MessageContent> parts) {
        List<Map<String, Object>> arr = new ArrayList<>(parts.size());
        for (MessageContent p : parts) {
            Map<String, Object> entry = new LinkedHashMap<>();
            switch (p.getType()) {
                case TEXT -> {
                    entry.put("type", "text");
                    entry.put("text", p.getValue());
                }
                case NET_IMAGE -> {
                    entry.put("type", "image_url");
                    Map<String, Object> img = new LinkedHashMap<>();
                    img.put("url", p.getValue());
                    entry.put("image_url", img);
                }
                case BASE64_IMAGE -> {
                    entry.put("type", "image_url");
                    Map<String, Object> img = new LinkedHashMap<>();
                    img.put("url", p.getValue().startsWith("data:image/")
                            ? p.getValue()
                            : "data:image/png;base64," + p.getValue());
                    entry.put("image_url", img);
                }
            }
            arr.add(entry);
        }
        return arr;
    }

    private List<ChatMessage> stripImages(List<ChatMessage> messages) {
        List<ChatMessage> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            List<MessageContent> kept = m.getContents().stream()
                    .filter(c -> !c.isImage())
                    .toList();
            if (kept.isEmpty()) {
                kept = List.of(MessageContent.text(""));
            }
            out.add(new ChatMessage(m.getRole(), kept));
        }
        return out;
    }

    private static String roleName(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private String resolveBaseUrl() {
        String configured = config().getBaseUrl();
        return StringUtils.isNotBlank(configured) ? configured : defaultBaseUrl();
    }

    private String resolveModel() {
        String configured = config().getModel();
        return StringUtils.isNotBlank(configured) ? configured : defaultModel();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Moonshot 等模型不接受网络图片 URL，需要把图片下载并转成 base64。子类可调用此工具方法。
     */
    protected void convertNetImagesToBase64(List<ChatMessage> messages) {
        for (ChatMessage m : messages) {
            for (int i = 0; i < m.getContents().size(); i++) {
                MessageContent part = m.getContents().get(i);
                if (part.getType() == MessageContent.Type.NET_IMAGE) {
                    try (InputStream in = restUtils.getFileInputStream(part.getValue())) {
                        String base64 = FileUtils.InputStreamToBase64(in);
                        m.getContents().set(i, MessageContent.base64Image(base64));
                    } catch (Exception e) {
                        log.warn("Failed to download image for base64 conversion: {}", part.getValue(), e);
                        m.getContents().set(i, MessageContent.text(""));
                    }
                }
            }
        }
    }
}
