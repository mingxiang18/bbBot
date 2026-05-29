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
import org.springframework.stereotype.Component;

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
 * OpenAI Chat Completion 协议的统一实现。每次调用按传入的 {@link ModelSpec}
 * 决定 baseUrl / apiKey / model / kind / vision —— 一个 bean 服务所有命名模型，
 * 不再每家一个子类。
 *
 * <p>统一处理：消息体序列化（含图像 part）、vision 关闭时过滤图像、moonshot 网络图转 base64、
 * HTTP 状态码到 {@link AIException.ErrorType} 的映射、指数退避重试、token 用量记录。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class OpenAiCompatProvider implements AIProvider {

    @Autowired
    private HttpExchanger httpExchanger;

    @Autowired
    private RestUtils restUtils;

    @Autowired
    private AIProviderProperties properties;

    @Autowired
    private TokenUsageRecorder tokenUsageRecorder;

    /** 指数退避重试委托给公共 {@link RetryExecutor}，语义与原内联循环一致。 */
    private final RetryExecutor retryExecutor = new RetryExecutor();

    /** 流式 chat 共享 HttpClient（线程安全）。 */
    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Override
    public String chat(ModelSpec spec, List<ChatMessage> messages) throws AIException {
        requireConfigured(spec);
        List<ChatMessage> processed = prepare(spec, messages);
        return executeWithRetry(spec, processed);
    }

    @Override
    public void chatStream(ModelSpec spec,
                           List<ChatMessage> messages,
                           List<ToolDefinition> tools,
                           StreamHandler handler) throws AIException {
        requireConfigured(spec);
        List<ChatMessage> processed = prepare(spec, messages);
        executeStreamWithRetry(spec, processed, tools, handler);
    }

    private void requireConfigured(ModelSpec spec) {
        if (spec == null || !spec.isConfigured()) {
            throw new AIException(AIException.ErrorType.UNAUTHORIZED,
                    "AI model not configured: " + (spec == null ? "null" : spec.getName()));
        }
    }

    /** moonshot 转 base64 + vision 关闭时剥图。 */
    private List<ChatMessage> prepare(ModelSpec spec, List<ChatMessage> messages) {
        List<ChatMessage> processed = new ArrayList<>(messages);
        if ("moonshot".equalsIgnoreCase(spec.getKind())) {
            convertNetImagesToBase64(processed);
        }
        if (!spec.isVision()) {
            processed = stripImages(processed);
        }
        return processed;
    }

    private void executeStreamWithRetry(ModelSpec spec,
                                        List<ChatMessage> messages,
                                        List<ToolDefinition> tools,
                                        StreamHandler handler) {
        retryExecutor.execute(properties.getRetry(),
                () -> {
                    doStreamCall(spec, messages, tools, handler);
                    return null;
                },
                (attempt, interval, e) -> log.warn(
                        "AI model [{}/{}] stream failed (attempt {}/{}, type={}, status={}): {}. Retrying in {}ms",
                        spec.getKind(), spec.getModel(), attempt, properties.getRetry().getMaxAttempts(),
                        e.getErrorType(), e.getHttpStatus(), e.getMessage(), interval),
                handler::onError);
    }

    /**
     * 真正发请求 + 解析 SSE。已收到任何 token / 工具调用后不再重试（避免重复输出）。
     */
    private void doStreamCall(ModelSpec spec,
                              List<ChatMessage> messages,
                              List<ToolDefinition> tools,
                              StreamHandler handler) {
        String url = baseUrl(spec) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", spec.getModel());
        body.put("messages", serializeMessages(messages));
        body.put("stream", true);
        if (properties.getUsage().isStreamIncludeUsage()) {
            Map<String, Object> streamOptions = new LinkedHashMap<>();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", serializeTools(tools));
            body.put("tool_choice", "auto");
        }
        String jsonBody = JSON.toJSONString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + spec.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        log.info("AI model [{}/{}] STREAM {} (msgs={}, tools={})",
                spec.getKind(), spec.getModel(), url, messages.size(), tools == null ? 0 : tools.size());

        StringBuilder fullText = new StringBuilder();
        Map<Integer, ToolCall> pendingToolCalls = new LinkedHashMap<>();
        String finishReason = null;
        boolean anyByteReceived = false;
        Usage lastUsage = null;

        try {
            HttpResponse<InputStream> resp = SHARED_HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw HttpErrorClassifier.classify(resp.statusCode(), tag(spec), errBody);
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
                    if (parsed.usage != null) lastUsage = parsed.usage;
                    if (parsed.finishReason != null) finishReason = parsed.finishReason;
                    if (parsed.textDelta != null && !parsed.textDelta.isEmpty()) {
                        fullText.append(parsed.textDelta);
                        try { handler.onTextDelta(parsed.textDelta); }
                        catch (Exception cbErr) { log.warn("StreamHandler.onTextDelta 异常", cbErr); }
                    }
                    if (parsed.reasoningDelta != null && !parsed.reasoningDelta.isEmpty()) {
                        try { handler.onReasoningDelta(parsed.reasoningDelta); }
                        catch (Exception cbErr) { log.warn("StreamHandler.onReasoningDelta 异常", cbErr); }
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
            if (anyByteReceived) {
                log.warn("AI model [{}/{}] stream interrupted after {} chars, treating as partial complete",
                        spec.getKind(), spec.getModel(), fullText.length(), e);
            } else {
                throw new AIException(AIException.ErrorType.RETRYABLE, -1,
                        "AI model [" + spec.getModel() + "] stream IO error: " + e.getMessage(), e);
            }
        }

        if (!pendingToolCalls.isEmpty()) {
            for (Map.Entry<Integer, ToolCall> e : pendingToolCalls.entrySet()) {
                ToolCall tc = e.getValue();
                if (tc.getId() == null || tc.getId().isEmpty()) {
                    String synthetic = "call_" + e.getKey() + "_" + Long.toHexString(System.nanoTime());
                    log.warn("AI model [{}] tool_call#{} 缺 id，合成 {}", spec.getModel(), e.getKey(), synthetic);
                    tc.setId(synthetic);
                }
            }
            try { handler.onToolCalls(new ArrayList<>(pendingToolCalls.values())); }
            catch (Exception cbErr) { log.warn("StreamHandler.onToolCalls 异常", cbErr); }
        }
        recordUsage(spec, lastUsage);
        try { handler.onComplete(fullText.toString(), finishReason); }
        catch (Exception cbErr) { log.warn("StreamHandler.onComplete 异常", cbErr); }
    }

    /** 把本次调用的 token 用量交给记录器异步落库。usage 为 null（网关没回）则跳过。 */
    private void recordUsage(ModelSpec spec, Usage usage) {
        if (usage == null) {
            return;
        }
        try {
            tokenUsageRecorder.record(spec.getKind(), spec.getModel(),
                    usage.prompt, usage.cached, usage.cacheWrite, usage.completion, usage.total);
        } catch (Exception e) {
            log.warn("token 用量记录失败（忽略）", e);
        }
    }

    /** SSE 单 chunk 解析结果。 */
    private static class SseChunkParsed {
        String textDelta;
        String reasoningDelta;
        List<ToolCall> toolCallDeltas;
        String finishReason;
        Usage usage;
    }

    /** token 用量。 */
    private static class Usage {
        int prompt;
        int completion;
        int total;
        /** 命中缓存（cache read）的输入 token，按 cache-hit 单价计。 */
        int cached;
        /** 写入缓存（cache creation）的输入 token，按 cache-write 单价计（仅 Anthropic 收费，其余 0）。 */
        int cacheWrite;
    }

    private SseChunkParsed parseSseChunk(String payload) {
        try {
            JSONObject root = JSON.parseObject(payload);
            Usage usage = parseUsage(root);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                if (usage != null) {
                    SseChunkParsed onlyUsage = new SseChunkParsed();
                    onlyUsage.usage = usage;
                    return onlyUsage;
                }
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            SseChunkParsed out = new SseChunkParsed();
            out.usage = usage;
            String finishReason = choice.getString("finish_reason");
            if (finishReason != null && !"null".equals(finishReason)) {
                out.finishReason = finishReason;
            }
            if (delta == null) return out;
            out.textDelta = delta.getString("content");
            out.reasoningDelta = delta.getString("reasoning_content");
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

    /** 从响应 JSON 顶层解析 usage（流式末帧 / 阻塞响应通用）。无则返回 null。 */
    private Usage parseUsage(JSONObject root) {
        JSONObject u = root.getJSONObject("usage");
        if (u == null) {
            return null;
        }
        Usage usage = new Usage();
        usage.prompt = u.getIntValue("prompt_tokens");
        usage.completion = u.getIntValue("completion_tokens");
        usage.total = u.getIntValue("total_tokens");
        // 命中缓存（cache read）token：deepseek=prompt_cache_hit_tokens；openai=prompt_tokens_details.cached_tokens；
        // kimi/anthropic=顶层 cached_tokens / cache_read_input_tokens
        int cached = u.getIntValue("prompt_cache_hit_tokens");
        if (cached == 0) {
            JSONObject details = u.getJSONObject("prompt_tokens_details");
            if (details != null) {
                cached = details.getIntValue("cached_tokens");
            }
        }
        if (cached == 0) {
            cached = u.getIntValue("cached_tokens");
        }
        if (cached == 0) {
            cached = u.getIntValue("cache_read_input_tokens");
        }
        usage.cached = cached;
        usage.cacheWrite = u.getIntValue("cache_creation_input_tokens");
        return usage;
    }

    private void mergeToolCallDelta(Map<Integer, ToolCall> agg, ToolCall delta) {
        int idx = delta.getIndex() == null ? 0 : delta.getIndex();
        ToolCall existing = agg.computeIfAbsent(idx, k -> ToolCall.builder()
                .index(k).id("").name("").argumentsJson("").build());
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

    private String executeWithRetry(ModelSpec spec, List<ChatMessage> messages) {
        return retryExecutor.execute(properties.getRetry(),
                () -> doCall(spec, messages),
                (attempt, interval, e) -> log.warn(
                        "AI model [{}/{}] call failed (attempt {}/{}, type={}, status={}): {}. Retrying in {}ms",
                        spec.getKind(), spec.getModel(), attempt, properties.getRetry().getMaxAttempts(),
                        e.getErrorType(), e.getHttpStatus(), e.getMessage(), interval),
                null);
    }

    private String doCall(ModelSpec spec, List<ChatMessage> messages) {
        String url = baseUrl(spec) + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", spec.getModel());
        body.put("messages", serializeMessages(messages));
        String jsonBody = JSON.toJSONString(body);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + spec.getApiKey());

        log.info("AI model [{}/{}] POST {} (msgs={})", spec.getKind(), spec.getModel(), url, messages.size());
        HttpExchanger.HttpResponse response;
        try {
            response = httpExchanger.post(url, headers, jsonBody);
        } catch (Exception e) {
            throw new AIException(AIException.ErrorType.RETRYABLE, -1,
                    "AI model [" + spec.getModel() + "] IO error: " + e.getMessage(), e);
        }

        if (response.status() / 100 == 2) {
            return parseSuccess(spec, response.body());
        }
        throw HttpErrorClassifier.classify(response.status(), tag(spec), response.body());
    }

    private String parseSuccess(ModelSpec spec, String responseBody) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AIException(AIException.ErrorType.FATAL,
                        "AI model [" + spec.getModel() + "] response has no choices: " + responseBody);
            }
            recordUsage(spec, parseUsage(root));
            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
            return content == null ? "" : content;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIException(AIException.ErrorType.FATAL,
                    "AI model [" + spec.getModel() + "] response parse error: " + e.getMessage(), e);
        }
    }

    /** 模型标识 {@code kind/model}，供 {@link HttpErrorClassifier} 拼接异常信息。 */
    private static String tag(ModelSpec spec) {
        return spec.getKind() + "/" + spec.getModel();
    }

    List<Map<String, Object>> serializeMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("role", roleName(m.getRole()));

            if (m.getRole() == ChatMessage.Role.TOOL) {
                obj.put("tool_call_id", m.getToolCallId());
                obj.put("content", flattenToString(m.getContents()));
                out.add(obj);
                continue;
            }

            List<MessageContent> parts = m.getContents();
            if (parts == null || parts.isEmpty()) {
                obj.put("content", null);
            } else if (parts.size() == 1 && parts.get(0).getType() == MessageContent.Type.TEXT) {
                obj.put("content", parts.get(0).getValue());
            } else {
                obj.put("content", serializeParts(parts));
            }

            if (m.getRole() == ChatMessage.Role.ASSISTANT
                    && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                obj.put("tool_calls", serializeToolCalls(m.getToolCalls()));
            }
            if (m.getRole() == ChatMessage.Role.ASSISTANT
                    && StringUtils.isNotEmpty(m.getReasoningContent())) {
                obj.put("reasoning_content", m.getReasoningContent());
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
            ChatMessage copy = new ChatMessage(m.getRole(), kept);
            copy.setToolCalls(m.getToolCalls());
            copy.setToolCallId(m.getToolCallId());
            copy.setReasoningContent(m.getReasoningContent());
            out.add(copy);
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

    private String baseUrl(ModelSpec spec) {
        String b = spec.getBaseUrl();
        if (StringUtils.isBlank(b)) {
            return "https://api.openai.com/v1";
        }
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    /** Moonshot 等模型不接受网络图片 URL，需要把图片下载并转成 base64。 */
    private void convertNetImagesToBase64(List<ChatMessage> messages) {
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
