package com.bb.bot.common.util.aiChat.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * Anthropic Messages API（{@code POST /v1/messages}）的官方实现。与 {@link OpenAiCompatProvider}
 * 协议不同点都在这里抹平，对上层 {@link AIProvider} 接口表现一致：
 *
 * <ul>
 *   <li>鉴权用 {@code x-api-key} + {@code anthropic-version}，不是 Bearer</li>
 *   <li>system 是顶层参数而非一条消息；TOOL 结果要塞进 user 消息的 tool_result 块，
 *       连续多个工具结果合并进同一条 user 消息</li>
 *   <li>工具调用是 assistant 消息里的 tool_use 块（{@code input} 是对象，不是 JSON 字符串）</li>
 *   <li>SSE 是带类型的事件流（message_start / content_block_delta / message_delta …），
 *       这里翻译成 {@link StreamHandler} 的 text/reasoning/toolCalls/finishReason，
 *       并把 stop_reason 映射成 OpenAI 风格的 finish_reason 供 ToolLoopExecutor 复用</li>
 *   <li>用量字段（input/output/cache_read/cache_creation）换算成 {@link TokenUsageRecorder} 的口径</li>
 * </ul>
 *
 * @author ren
 */
@Slf4j
@Component
public class AnthropicProvider implements AIProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";

    @Autowired
    private HttpExchanger httpExchanger;

    @Autowired
    private AIProviderProperties properties;

    @Autowired
    private TokenUsageRecorder tokenUsageRecorder;

    /** Messages API 必填 max_tokens；ModelSpec 无此字段，用统一默认（可被 ai.anthropic.max-tokens 覆盖）。 */
    @Value("${ai.anthropic.max-tokens:4096}")
    private int maxTokens;

    /** 指数退避重试委托给公共 {@link RetryExecutor}，语义与原内联循环一致。 */
    private final RetryExecutor retryExecutor = new RetryExecutor();

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

    /** vision 关闭时剥图（Anthropic 原生收 url 图，无需 base64 转换）。 */
    private List<ChatMessage> prepare(ModelSpec spec, List<ChatMessage> messages) {
        if (spec.isVision()) {
            return new ArrayList<>(messages);
        }
        return stripImages(messages);
    }

    // ---- 阻塞 ----

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
        String url = baseUrl(spec) + "/messages";
        String jsonBody = JSON.toJSONString(buildBody(spec, messages, null, false));

        Map<String, String> headers = baseHeaders(spec);
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
            recordUsage(spec, parseUsage(root.getJSONObject("usage")));
            JSONArray content = root.getJSONArray("content");
            if (content == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.size(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.getString("type"))) {
                    String t = block.getString("text");
                    if (t != null) sb.append(t);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new AIException(AIException.ErrorType.FATAL,
                    "AI model [" + spec.getModel() + "] response parse error: " + e.getMessage(), e);
        }
    }

    // ---- 流式 ----

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

    private void doStreamCall(ModelSpec spec,
                              List<ChatMessage> messages,
                              List<ToolDefinition> tools,
                              StreamHandler handler) {
        String url = baseUrl(spec) + "/messages";
        String jsonBody = JSON.toJSONString(buildBody(spec, messages, tools, true));

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        baseHeaders(spec).forEach(reqBuilder::header);
        HttpRequest req = reqBuilder.build();

        log.info("AI model [{}/{}] STREAM {} (msgs={}, tools={})",
                spec.getKind(), spec.getModel(), url, messages.size(), tools == null ? 0 : tools.size());

        StringBuilder fullText = new StringBuilder();
        Map<Integer, ToolCall> toolCalls = new LinkedHashMap<>();
        String[] finishReason = {null};
        Usage usage = new Usage();
        boolean anyByteReceived = false;

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
                    if (payload.isEmpty()) continue;
                    anyByteReceived = true;
                    handleEvent(payload, handler, fullText, toolCalls, finishReason, usage);
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

        if (!toolCalls.isEmpty()) {
            for (Map.Entry<Integer, ToolCall> e : toolCalls.entrySet()) {
                ToolCall tc = e.getValue();
                if (StringUtils.isEmpty(tc.getId())) {
                    tc.setId("toolu_" + e.getKey() + "_" + Long.toHexString(System.nanoTime()));
                }
                if (StringUtils.isEmpty(tc.getArgumentsJson())) {
                    tc.setArgumentsJson("{}");
                }
            }
            try { handler.onToolCalls(new ArrayList<>(toolCalls.values())); }
            catch (Exception cbErr) { log.warn("StreamHandler.onToolCalls 异常", cbErr); }
        }
        recordUsage(spec, usage);
        try { handler.onComplete(fullText.toString(), finishReason[0]); }
        catch (Exception cbErr) { log.warn("StreamHandler.onComplete 异常", cbErr); }
    }

    /** 解析一条 SSE data 事件，按 type 累积文本 / 工具调用 / finishReason / 用量。 */
    private void handleEvent(String payload,
                             StreamHandler handler,
                             StringBuilder fullText,
                             Map<Integer, ToolCall> toolCalls,
                             String[] finishReason,
                             Usage usage) {
        JSONObject obj;
        try {
            obj = JSON.parseObject(payload);
        } catch (Exception e) {
            log.debug("SSE chunk parse failed: {}", payload, e);
            return;
        }
        String type = obj.getString("type");
        if (type == null) return;
        switch (type) {
            case "message_start" -> {
                JSONObject msg = obj.getJSONObject("message");
                if (msg != null) accumulateInputUsage(msg.getJSONObject("usage"), usage);
            }
            case "content_block_start" -> {
                int idx = obj.getIntValue("index");
                JSONObject cb = obj.getJSONObject("content_block");
                if (cb != null && "tool_use".equals(cb.getString("type"))) {
                    toolCalls.put(idx, ToolCall.builder()
                            .index(idx)
                            .id(cb.getString("id"))
                            .name(cb.getString("name"))
                            .argumentsJson("")
                            .build());
                }
            }
            case "content_block_delta" -> {
                int idx = obj.getIntValue("index");
                JSONObject delta = obj.getJSONObject("delta");
                if (delta == null) return;
                switch (StringUtils.defaultString(delta.getString("type"))) {
                    case "text_delta" -> {
                        String t = delta.getString("text");
                        if (StringUtils.isNotEmpty(t)) {
                            fullText.append(t);
                            try { handler.onTextDelta(t); }
                            catch (Exception cbErr) { log.warn("StreamHandler.onTextDelta 异常", cbErr); }
                        }
                    }
                    case "input_json_delta" -> {
                        ToolCall tc = toolCalls.get(idx);
                        String partial = delta.getString("partial_json");
                        if (tc != null && partial != null) {
                            tc.setArgumentsJson((tc.getArgumentsJson() == null ? "" : tc.getArgumentsJson()) + partial);
                        }
                    }
                    case "thinking_delta" -> {
                        String th = delta.getString("thinking");
                        if (StringUtils.isNotEmpty(th)) {
                            try { handler.onReasoningDelta(th); }
                            catch (Exception cbErr) { log.warn("StreamHandler.onReasoningDelta 异常", cbErr); }
                        }
                    }
                    default -> { }
                }
            }
            case "message_delta" -> {
                JSONObject delta = obj.getJSONObject("delta");
                if (delta != null) {
                    String stop = delta.getString("stop_reason");
                    if (stop != null) finishReason[0] = mapStopReason(stop);
                }
                JSONObject u = obj.getJSONObject("usage");
                if (u != null) usage.completion = u.getIntValue("output_tokens");
            }
            case "error" -> {
                JSONObject err = obj.getJSONObject("error");
                String errType = err == null ? "" : StringUtils.defaultString(err.getString("type"));
                String message = err == null ? payload : err.getString("message");
                AIException.ErrorType et = ("overloaded_error".equals(errType) || "api_error".equals(errType))
                        ? AIException.ErrorType.RETRYABLE
                        : AIException.ErrorType.FATAL;
                throw new AIException(et, "AI model anthropic stream error [" + errType + "]: " + message);
            }
            default -> { }
        }
    }

    /** stop_reason → OpenAI 风格 finish_reason，供 ToolLoopExecutor 复用判断。 */
    private static String mapStopReason(String stop) {
        return switch (stop) {
            case "tool_use" -> "tool_calls";
            case "end_turn", "stop_sequence" -> "stop";
            case "max_tokens" -> "length";
            default -> stop;
        };
    }

    // ---- 请求体构建 ----

    private Map<String, Object> buildBody(ModelSpec spec,
                                          List<ChatMessage> messages,
                                          List<ToolDefinition> tools,
                                          boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", spec.getModel());
        body.put("max_tokens", maxTokens);

        StringBuilder system = new StringBuilder();
        List<Map<String, Object>> msgs = serializeMessages(messages, system);
        if (system.length() > 0) {
            body.put("system", system.toString());
        }
        body.put("messages", msgs);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", serializeTools(tools));
            body.put("tool_choice", Map.of("type", "auto"));
        }
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /**
     * ChatMessage 列表 → Anthropic messages（system 抽到顶层，连续同 role 合并块）。
     * SYSTEM 累积进 {@code systemOut}；TOOL 变成 user 消息里的 tool_result 块。
     */
    private List<Map<String, Object>> serializeMessages(List<ChatMessage> messages, StringBuilder systemOut) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            switch (m.getRole()) {
                case SYSTEM -> {
                    String t = flattenText(m.getContents());
                    if (StringUtils.isNotEmpty(t)) {
                        if (systemOut.length() > 0) systemOut.append("\n\n");
                        systemOut.append(t);
                    }
                }
                case USER -> appendBlocks(out, "user", serializeParts(m.getContents()));
                case ASSISTANT -> {
                    List<Map<String, Object>> blocks = new ArrayList<>();
                    String text = flattenText(m.getContents());
                    if (StringUtils.isNotEmpty(text)) {
                        blocks.add(textBlock(text));
                    }
                    if (m.getToolCalls() != null) {
                        for (ToolCall c : m.getToolCalls()) {
                            Map<String, Object> tu = new LinkedHashMap<>();
                            tu.put("type", "tool_use");
                            tu.put("id", c.getId());
                            tu.put("name", c.getName());
                            tu.put("input", parseInput(c.getArgumentsJson()));
                            blocks.add(tu);
                        }
                    }
                    if (blocks.isEmpty()) {
                        blocks.add(textBlock(""));
                    }
                    appendBlocks(out, "assistant", blocks);
                }
                case TOOL -> {
                    Map<String, Object> tr = new LinkedHashMap<>();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", m.getToolCallId());
                    tr.put("content", flattenText(m.getContents()));
                    appendBlocks(out, "user", List.of(tr));
                }
            }
        }
        return out;
    }

    /** 追加一条消息；与上一条 role 相同则合并内容块（Anthropic 要求 user/assistant 交替）。 */
    @SuppressWarnings("unchecked")
    private void appendBlocks(List<Map<String, Object>> out, String role, List<Map<String, Object>> blocks) {
        if (!out.isEmpty()) {
            Map<String, Object> prev = out.get(out.size() - 1);
            if (role.equals(prev.get("role"))) {
                ((List<Map<String, Object>>) prev.get("content")).addAll(blocks);
                return;
            }
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", new ArrayList<>(blocks));
        out.add(msg);
    }

    private List<Map<String, Object>> serializeParts(List<MessageContent> parts) {
        List<Map<String, Object>> arr = new ArrayList<>();
        if (parts == null) return arr;
        for (MessageContent p : parts) {
            switch (p.getType()) {
                case TEXT -> {
                    if (StringUtils.isNotEmpty(p.getValue())) arr.add(textBlock(p.getValue()));
                }
                case NET_IMAGE -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("type", "url");
                    source.put("url", p.getValue());
                    arr.add(imageBlock(source));
                }
                case BASE64_IMAGE -> arr.add(imageBlock(base64Source(p.getValue())));
            }
        }
        if (arr.isEmpty()) arr.add(textBlock(""));
        return arr;
    }

    /** data:image/<mt>;base64,<data> 拆成 Anthropic 的 base64 source；无前缀按 image/png。 */
    private Map<String, Object> base64Source(String value) {
        String mediaType = "image/png";
        String data = value == null ? "" : value;
        if (data.startsWith("data:") && data.contains(";base64,")) {
            int semi = data.indexOf(';');
            mediaType = data.substring(5, semi);
            data = data.substring(data.indexOf(";base64,") + 8);
        }
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "base64");
        source.put("media_type", mediaType);
        source.put("data", data);
        return source;
    }

    private static Map<String, Object> imageBlock(Map<String, Object> source) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image");
        block.put("source", source);
        return block;
    }

    private static Map<String, Object> textBlock(String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text == null ? "" : text);
        return block;
    }

    private List<Map<String, Object>> serializeTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> arr = new ArrayList<>(tools.size());
        for (ToolDefinition t : tools) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", t.getName());
            tool.put("description", t.getDescription());
            tool.put("input_schema", t.getParametersSchema());
            arr.add(tool);
        }
        return arr;
    }

    private Object parseInput(String argumentsJson) {
        if (StringUtils.isBlank(argumentsJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(argumentsJson);
        } catch (Exception e) {
            log.warn("tool_use input 非合法 JSON，按空对象传：{}", argumentsJson);
            return new LinkedHashMap<>();
        }
    }

    private String flattenText(List<MessageContent> parts) {
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (MessageContent p : parts) {
            if (p.getType() == MessageContent.Type.TEXT && p.getValue() != null) {
                sb.append(p.getValue());
            }
        }
        return sb.toString();
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

    // ---- 用量 ----

    /** token 用量（与 OpenAiCompatProvider 同口径：prompt 含 cache-hit、不含 cache-write）。 */
    private static class Usage {
        int prompt;
        int completion;
        int cached;
        int cacheWrite;
    }

    /** message_start 的 usage：累积输入侧 token（输出 token 在 message_delta 才有）。 */
    private void accumulateInputUsage(JSONObject u, Usage usage) {
        if (u == null) return;
        usage.cached = u.getIntValue("cache_read_input_tokens");
        usage.cacheWrite = u.getIntValue("cache_creation_input_tokens");
        usage.prompt = u.getIntValue("input_tokens") + usage.cached;
    }

    /** 阻塞响应的顶层 usage：input/output 一帧给全。 */
    private Usage parseUsage(JSONObject u) {
        if (u == null) return null;
        Usage usage = new Usage();
        usage.cached = u.getIntValue("cache_read_input_tokens");
        usage.cacheWrite = u.getIntValue("cache_creation_input_tokens");
        usage.prompt = u.getIntValue("input_tokens") + usage.cached;
        usage.completion = u.getIntValue("output_tokens");
        return usage;
    }

    private void recordUsage(ModelSpec spec, Usage usage) {
        if (usage == null || (usage.prompt == 0 && usage.completion == 0 && usage.cacheWrite == 0)) {
            return;
        }
        try {
            int total = usage.prompt + usage.completion + usage.cacheWrite;
            tokenUsageRecorder.record(spec.getKind(), spec.getModel(),
                    usage.prompt, usage.cached, usage.cacheWrite, usage.completion, total);
        } catch (Exception e) {
            log.warn("token 用量记录失败（忽略）", e);
        }
    }

    // ---- 公共 ----

    private Map<String, String> baseHeaders(ModelSpec spec) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.put("x-api-key", spec.getApiKey());
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    /** 模型标识 {@code kind/model}，供 {@link HttpErrorClassifier} 拼接异常信息。 */
    private static String tag(ModelSpec spec) {
        return spec.getKind() + "/" + spec.getModel();
    }

    private String baseUrl(ModelSpec spec) {
        String b = spec.getBaseUrl();
        if (StringUtils.isBlank(b)) {
            return DEFAULT_BASE_URL;
        }
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
