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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /** 流式 chat 共享 HttpClient（线程安全）。 */
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

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

    @Override
    public void chatStream(List<ChatMessage> messages,
                            List<ToolDefinition> tools,
                            StreamHandler handler) throws AIException {
        if (!isConfigured()) {
            throw new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "AI provider [" + name() + "] api key not configured");
        }
        List<ChatMessage> processed = new ArrayList<>(messages);
        preprocessImages(processed);
        if (!config().isVisionEnable()) {
            processed = stripImages(processed);
        }
        executeStreamWithRetry(processed, tools, handler);
    }

    private void executeStreamWithRetry(List<ChatMessage> messages,
                                         List<ToolDefinition> tools,
                                         StreamHandler handler) {
        AIProviderProperties.RetryConfig retry = properties.getRetry();
        long interval = retry.getInitialIntervalMs();
        AIException last = null;
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            try {
                doStreamCall(messages, tools, handler);
                return;
            } catch (AIException e) {
                last = e;
                if (!e.isRetryable() || attempt == retry.getMaxAttempts()) {
                    handler.onError(e);
                    throw e;
                }
                log.warn("AI provider [{}] stream failed (attempt {}/{}, type={}, status={}): {}. Retrying in {}ms",
                        name(), attempt, retry.getMaxAttempts(), e.getErrorType(), e.getHttpStatus(), e.getMessage(), interval);
                sleep(interval);
                interval = Math.min((long) (interval * retry.getMultiplier()), retry.getMaxIntervalMs());
            }
        }
        if (last != null) {
            handler.onError(last);
            throw last;
        }
    }

    /**
     * 真正发请求 + 解析 SSE。每次重试调一次。
     * 已收到任何 token / 工具调用后，不再重试（避免重复输出）—— retry 仅覆盖握手 / 0 字节失败。
     */
    private void doStreamCall(List<ChatMessage> messages,
                               List<ToolDefinition> tools,
                               StreamHandler handler) {
        String url = resolveBaseUrl() + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel());
        body.put("messages", serializeMessages(messages));
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", serializeTools(tools));
            body.put("tool_choice", "auto");
        }
        String jsonBody = JSON.toJSONString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config().getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        log.info("AI provider [{}] STREAM {} (model={}, msgs={}, tools={})",
                name(), url, resolveModel(), messages.size(), tools == null ? 0 : tools.size());

        StringBuilder fullText = new StringBuilder();
        Map<Integer, ToolCall> pendingToolCalls = new LinkedHashMap<>();
        String finishReason = null;
        boolean anyByteReceived = false;

        try {
            HttpResponse<InputStream> resp = SHARED_HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw classify(resp.statusCode(), errBody);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    anyByteReceived = true;
                    SseChunkParsed parsed = parseSseChunk(payload);
                    if (parsed == null) continue;
                    if (parsed.finishReason != null) finishReason = parsed.finishReason;
                    if (parsed.textDelta != null && !parsed.textDelta.isEmpty()) {
                        fullText.append(parsed.textDelta);
                        try { handler.onTextDelta(parsed.textDelta); }
                        catch (Exception cbErr) { log.warn("StreamHandler.onTextDelta 异常", cbErr); }
                    }
                    if (parsed.toolCallDeltas != null) {
                        for (ToolCall delta : parsed.toolCallDeltas) {
                            mergeToolCallDelta(pendingToolCalls, delta);
                        }
                    }
                }
            }
        } catch (AIException ae) {
            throw ae;
        } catch (Exception e) {
            // 网络层异常：已收到字节就当部分完成，不让重试覆盖；零字节就 RETRYABLE
            if (anyByteReceived) {
                log.warn("AI provider [{}] stream interrupted after {} chars, treating as partial complete",
                        name(), fullText.length(), e);
            } else {
                throw new AIException(AIException.ErrorType.RETRYABLE, -1,
                        "AI provider [" + name() + "] stream IO error: " + e.getMessage(), e);
            }
        }

        if (!pendingToolCalls.isEmpty()) {
            // 兜底：部分 OpenAI 兼容 provider（含某些代理）在 SSE 增量里不带 id，
            // 聚合结束后 id 仍是空串。下一轮回灌时 tool 消息的 tool_call_id 会被
            // API 视为 missing，整条请求被拒。这里合成一个稳定 id，并把同一 id
            // 同时回写到 assistant.tool_calls[].id 与 tool.tool_call_id（共享同
            // 一 ToolCall 引用），确保前后呼应。
            for (Map.Entry<Integer, ToolCall> e : pendingToolCalls.entrySet()) {
                ToolCall tc = e.getValue();
                if (tc.getId() == null || tc.getId().isEmpty()) {
                    String synthetic = "call_" + e.getKey() + "_" + Long.toHexString(System.nanoTime());
                    log.warn("AI provider [{}] tool_call#{} 缺 id，合成 {}", name(), e.getKey(), synthetic);
                    tc.setId(synthetic);
                }
            }
            try { handler.onToolCalls(new ArrayList<>(pendingToolCalls.values())); }
            catch (Exception cbErr) { log.warn("StreamHandler.onToolCalls 异常", cbErr); }
        }
        try { handler.onComplete(fullText.toString(), finishReason); }
        catch (Exception cbErr) { log.warn("StreamHandler.onComplete 异常", cbErr); }
    }

    /** SSE 单 chunk 解析结果。 */
    private static class SseChunkParsed {
        String textDelta;
        List<ToolCall> toolCallDeltas;
        String finishReason;
    }

    private SseChunkParsed parseSseChunk(String payload) {
        try {
            JSONObject root = JSON.parseObject(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            SseChunkParsed out = new SseChunkParsed();
            String finishReason = choice.getString("finish_reason");
            if (finishReason != null && !"null".equals(finishReason)) {
                out.finishReason = finishReason;
            }
            if (delta == null) return out;
            out.textDelta = delta.getString("content");
            JSONArray toolCallsDelta = delta.getJSONArray("tool_calls");
            if (toolCallsDelta != null) {
                List<ToolCall> deltas = new ArrayList<>();
                for (int i = 0; i < toolCallsDelta.size(); i++) {
                    JSONObject tcd = toolCallsDelta.getJSONObject(i);
                    Integer idx = tcd.getInteger("index");
                    if (idx == null) idx = 0;
                    String id = tcd.getString("id");
                    String fnName = null, fnArgs = null;
                    JSONObject fn = tcd.getJSONObject("function");
                    if (fn != null) {
                        fnName = fn.getString("name");
                        fnArgs = fn.getString("arguments");
                    }
                    deltas.add(ToolCall.builder().index(idx).id(id).name(fnName).argumentsJson(fnArgs).build());
                }
                out.toolCallDeltas = deltas;
            }
            return out;
        } catch (Exception e) {
            log.debug("SSE chunk parse failed: {}", payload, e);
            return null;
        }
    }

    private void mergeToolCallDelta(Map<Integer, ToolCall> agg, ToolCall delta) {
        int idx = delta.getIndex() == null ? 0 : delta.getIndex();
        ToolCall existing = agg.computeIfAbsent(idx, k -> ToolCall.builder()
                .index(k).id("").name("").argumentsJson("").build());
        // 仅当 delta 带了非空 id 才覆盖：避免后续 chunk 的 id 缺省时把头一片的真实 id 抹掉
        if (delta.getId() != null && !delta.getId().isEmpty()) existing.setId(delta.getId());
        if (delta.getName() != null) {
            existing.setName((existing.getName() == null ? "" : existing.getName()) + delta.getName());
        }
        if (delta.getArgumentsJson() != null) {
            existing.setArgumentsJson(
                    (existing.getArgumentsJson() == null ? "" : existing.getArgumentsJson()) + delta.getArgumentsJson());
        }
    }

    private List<Map<String, Object>> serializeTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> arr = new ArrayList<>(tools.size());
        for (ToolDefinition t : tools) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.getName());
            fn.put("description", t.getDescription());
            fn.put("parameters", t.getParametersSchema());
            wrap.put("function", fn);
            arr.add(wrap);
        }
        return arr;
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

    /** 包级可见：被 chatStream 路径复用。 */
    List<Map<String, Object>> serializeMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("role", roleName(m.getRole()));

            // tool 角色单独处理：必须有 tool_call_id 字段，content 是纯字符串
            if (m.getRole() == ChatMessage.Role.TOOL) {
                obj.put("tool_call_id", m.getToolCallId());
                obj.put("content", flattenToString(m.getContents()));
                out.add(obj);
                continue;
            }

            // content：纯文本走 string，否则走 parts 数组（图片）
            List<MessageContent> parts = m.getContents();
            if (parts == null || parts.isEmpty()) {
                obj.put("content", null);
            } else if (parts.size() == 1 && parts.get(0).getType() == MessageContent.Type.TEXT) {
                obj.put("content", parts.get(0).getValue());
            } else {
                obj.put("content", serializeParts(parts));
            }

            // assistant 消息的 tool_calls（function calling 协议）
            if (m.getRole() == ChatMessage.Role.ASSISTANT
                    && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                obj.put("tool_calls", serializeToolCalls(m.getToolCalls()));
            }
            out.add(obj);
        }
        return out;
    }

    private String flattenToString(List<MessageContent> parts) {
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MessageContent p : parts) {
            if (p.getType() == MessageContent.Type.TEXT && p.getValue() != null) {
                sb.append(p.getValue());
            }
        }
        return sb.toString();
    }

    private List<Map<String, Object>> serializeToolCalls(List<ToolCall> calls) {
        List<Map<String, Object>> arr = new ArrayList<>(calls.size());
        for (ToolCall c : calls) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", c.getId());
            entry.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.getName());
            fn.put("arguments", c.getArgumentsJson() == null ? "{}" : c.getArgumentsJson());
            entry.put("function", fn);
            arr.add(entry);
        }
        return arr;
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
            case TOOL -> "tool";
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
