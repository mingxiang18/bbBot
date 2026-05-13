package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ai聊天工具类
 * @author ren
 */
@Slf4j
@Component
public class AiChatClient {

    @Autowired
    private RestUtils restUtils;

    /**
     * chatGPT的Url
     */
    @Value("${chatGPT.url:https://api.openai.com/v1/chat/completions}")
    private String chatGPTUrl;

    /**
     * chatGPT的apiKey
     */
    @Value("${chatGPT.apiKey:}")
    private String chatGPTApiKey;

    /**
     * 模型
     */
    @Value("${chatGPT.model:gpt-4}")
    private String model;

    /**
     * ai模型视觉开关，如果模型支持图像输入可开启
     */
    @Value("${chatGPT.visionEnable:false}")
    private Boolean visionEnable;

    /**
     * 出现错误时重试次数
     */
    @Value("${chatGPT.retryNum:10}")
    private Integer retryNum;

    /**
     * 是否配置了ai
     */
    public Boolean hasConfigAI() {
        return StringUtils.isNoneBlank(chatGPTApiKey);
    }

    /**
     * 询问chatGPT
     * @Param personality 机器人设定/性格
     * @Param question 问题
     * @Param chatHistoryList 聊天历史
     * @author ren
     */
    public String askChatGPT(List<ChatGPTContent> chatContentList) {
        //如果apiKey为空，不执行
        if (StringUtils.isBlank(chatGPTApiKey)) {
            return null;
        }

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.set("Authorization", "Bearer " + chatGPTApiKey);

        //图片url二次处理
        Iterator<ChatGPTContent> iterator = chatContentList.iterator(); // 实例化迭代器
        while (iterator.hasNext()) {
            ChatGPTContent chatGPTContent = iterator.next(); // 读取当前集合数据元素
            if (chatGPTContent.getContent() instanceof List<?> contentList) {
                for (Object content : contentList) {
                    if (content instanceof Map imageMap) {
                        if (imageMap.get("type").equals("image_url")) {
                            Map imageUrlMap = (Map) imageMap.get("image_url");
                            String imageUrl = (String) imageUrlMap.get("url");
                            if (StringUtils.isNotBlank(imageUrl)) {
                                boolean isBase64 = imageUrl.startsWith("data:image/");
                                //如果是moonshot模型，且图片不是base64，把网络图片下载后转成base64发送
                                if (model.contains("moonshot") && !isBase64) {
                                    try (InputStream inputStream = restUtils.getFileInputStream(imageUrl);) {
                                        //替换原来的url
                                        imageUrlMap.put("url", "data:image/png;base64," + FileUtils.InputStreamToBase64(inputStream));
                                    } catch (Exception e) {
                                        log.error("下载图片失败", e);
                                        return "图片过期啦，可以重新发一下图片再试试噢！";
                                    }
                                }else if (!model.contains("moonshot") && isBase64){
                                    //如果不是moonshot模型，去掉所有base64格式的图像
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!visionEnable) {
            //如果模型不支持图像输入，要把图像输入去掉，仅保留文本
            chatContentList.forEach(chatGPTContent -> {
                if (chatGPTContent.getContent() instanceof List<?> contentList) {
                    contentList.removeIf(content -> content instanceof Map imageMap && imageMap.get("type").equals("image_url"));
                }
            });
        }

        int nowRetryNum = retryNum;
        while (nowRetryNum > 0) {
            try {
                //发送请求
                JSONObject chatGPTResponse = restUtils.post(chatGPTUrl, httpHeaders, new ChatGPTRequest(model, chatContentList), JSONObject.class);
                //返回chatGPT回复
                return chatGPTResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } catch (Exception e) {
                log.error("chatGPT请求失败，剩余重试次数：" + nowRetryNum, e);
            } finally {
                nowRetryNum--;
            }
        }
        return "";
    }

    /**
     * SSE 流式询问 ChatGPT。
     *
     * <p>调用方负责消费 onDelta（增量文本）和最终 onComplete（完整文本）。失败时调 onError。
     * 本方法阻塞当前线程直到流结束或失败 —— 调用方应在工作线程里调用，避免阻塞主流程。</p>
     *
     * @param chatContentList 聊天上下文
     * @param onDelta         每个 token 增量回调（可能频繁触发）
     * @param onComplete      完整聚合文本（流自然结束时调用一次）
     * @param onError         异常回调
     */
    public void askChatGPTStream(List<ChatGPTContent> chatContentList,
                                  Consumer<String> onDelta,
                                  Consumer<String> onComplete,
                                  Consumer<Throwable> onError) {
        if (StringUtils.isBlank(chatGPTApiKey)) {
            onError.accept(new IllegalStateException("chatGPT.apiKey 未配置"));
            return;
        }

        // 复用 askChatGPT 的图片预处理逻辑
        preprocessImageContent(chatContentList);

        ChatGPTRequest requestBody = new ChatGPTRequest(model, chatContentList);
        requestBody.setStream(true);
        String requestJson = JSON.toJSONString(requestBody);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatGPTUrl))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + chatGPTApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        StringBuilder fullText = new StringBuilder();
        try {
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String bodyText = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                onError.accept(new RuntimeException("chatGPT 流式请求 HTTP " + response.statusCode() + ": " + bodyText));
                return;
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    String deltaContent = parseDeltaContent(payload);
                    if (deltaContent != null && !deltaContent.isEmpty()) {
                        fullText.append(deltaContent);
                        try {
                            onDelta.accept(deltaContent);
                        } catch (Exception cbErr) {
                            log.warn("askChatGPTStream onDelta 回调异常", cbErr);
                        }
                    }
                }
            }
            onComplete.accept(fullText.toString());
        } catch (Exception e) {
            log.error("askChatGPTStream 失败", e);
            // 已经吐出来的内容也算部分成功，先 complete 已收的内容再 error
            if (fullText.length() > 0) {
                try {
                    onComplete.accept(fullText.toString());
                } catch (Exception ignore) {
                }
            }
            onError.accept(e);
        }
    }

    /**
     * 流式 + tool calling 循环。
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>构造请求（带 tools 字段 + stream=true），POST → 接 SSE</li>
     *   <li>遍历 delta：纯文本 delta 通过 onDelta 实时吐出；tool_calls delta 按 index 聚合</li>
     *   <li>finish_reason="stop" → onComplete 调一次，循环结束</li>
     *   <li>finish_reason="tool_calls" → 把 assistant 工具调用消息 + 每个工具的执行结果 message 追加到上下文，下一轮请求</li>
     *   <li>循环达到 maxSteps 强制收尾</li>
     * </ol>
     *
     * <p>onDelta 只在 LLM 实际吐字时调用 —— 工具调用阶段 LLM 不吐字，对应静默期。</p>
     */
    public void askChatGPTStreamWithTools(
            List<ChatGPTContent> messages,
            List<Object> tools,
            java.util.function.BiFunction<String, String, String> toolInvoker,
            Consumer<String> onDelta,
            Consumer<String> onComplete,
            Consumer<Throwable> onError,
            int maxSteps) {
        if (StringUtils.isBlank(chatGPTApiKey)) {
            onError.accept(new IllegalStateException("chatGPT.apiKey 未配置"));
            return;
        }
        preprocessImageContent(messages);

        StringBuilder accumulatedText = new StringBuilder();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        try {
            for (int step = 0; step < maxSteps; step++) {
                ChatGPTRequest body = new ChatGPTRequest(model, messages);
                body.setStream(true);
                if (tools != null && !tools.isEmpty()) {
                    body.setTools(tools);
                    body.setTool_choice("auto");
                }
                String reqJson = JSON.toJSONString(body);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(chatGPTUrl))
                        .timeout(Duration.ofSeconds(120))
                        .header("Authorization", "Bearer " + chatGPTApiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(reqJson, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    String bodyText = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                    onError.accept(new RuntimeException("chatGPT HTTP " + resp.statusCode() + ": " + bodyText));
                    return;
                }

                // 解析这一步的 SSE
                StepResult result = consumeSseStep(resp.body(), accumulatedText, onDelta);

                if ("stop".equals(result.finishReason) || result.finishReason == null) {
                    onComplete.accept(accumulatedText.toString());
                    return;
                }

                if ("tool_calls".equals(result.finishReason)) {
                    // 把 assistant 的 tool_calls 决策追加到上下文
                    ChatGPTContent assistantMsg = new ChatGPTContent();
                    assistantMsg.setRole(ChatGPTContent.ASSISTANT_ROLE);
                    assistantMsg.setContent(result.assistantTextThisStep.length() == 0 ? null : result.assistantTextThisStep.toString());
                    assistantMsg.setTool_calls(new java.util.ArrayList<>(result.toolCalls.values()));
                    messages.add(assistantMsg);

                    // 串行执行每个工具
                    for (Map<String, Object> toolCall : result.toolCalls.values()) {
                        String toolId = (String) toolCall.get("id");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                        String toolName = function == null ? null : (String) function.get("name");
                        String argsJson = function == null ? null : (String) function.get("arguments");
                        String toolResult;
                        try {
                            toolResult = toolInvoker.apply(toolName, argsJson);
                        } catch (Exception toolErr) {
                            log.warn("工具 {} 调用器异常", toolName, toolErr);
                            toolResult = "{\"error\":\"executor_threw\",\"message\":" + JSON.toJSONString(toolErr.getMessage()) + "}";
                        }
                        ChatGPTContent toolMsg = new ChatGPTContent();
                        toolMsg.setRole(ChatGPTContent.TOOL_ROLE);
                        toolMsg.setContent(toolResult);
                        toolMsg.setTool_call_id(toolId);
                        messages.add(toolMsg);
                    }
                    // 进入下一轮
                    continue;
                }

                // 其他 finish_reason（length / content_filter 等）：当 stop 处理
                onComplete.accept(accumulatedText.toString());
                return;
            }
            // maxSteps 用尽
            log.warn("askChatGPTStreamWithTools 达到 maxSteps={}，强制收尾", maxSteps);
            accumulatedText.append("\n[已达工具调用步数上限，停止]");
            onComplete.accept(accumulatedText.toString());
        } catch (Exception e) {
            log.error("askChatGPTStreamWithTools 失败", e);
            if (accumulatedText.length() > 0) {
                try {
                    onComplete.accept(accumulatedText.toString());
                } catch (Exception ignore) {}
            }
            onError.accept(e);
        }
    }

    /** 一次 SSE step 的解析结果。 */
    private static class StepResult {
        String finishReason;
        StringBuilder assistantTextThisStep = new StringBuilder();
        /** key=index, value=tool_call object（按 OpenAI 协议）。 */
        Map<Integer, Map<String, Object>> toolCalls = new LinkedHashMap<>();
    }

    /**
     * 消费一次 SSE 流，把文本 delta 立即喷给 onDelta + 累计到 accumulatedText，
     * 同时聚合 tool_calls delta，返回最终的 finish_reason 和 tool_calls。
     */
    private StepResult consumeSseStep(InputStream stream,
                                       StringBuilder accumulatedText,
                                       Consumer<String> onDelta) throws java.io.IOException {
        StepResult sr = new StepResult();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                try {
                    JSONObject obj = JSON.parseObject(payload);
                    JSONArray choices = obj.getJSONArray("choices");
                    if (choices == null || choices.isEmpty()) continue;
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.getJSONObject("delta");
                    String finish = choice.getString("finish_reason");
                    if (finish != null && !"null".equals(finish)) {
                        sr.finishReason = finish;
                    }
                    if (delta == null) continue;
                    // 普通文本
                    String text = delta.getString("content");
                    if (text != null && !text.isEmpty()) {
                        accumulatedText.append(text);
                        sr.assistantTextThisStep.append(text);
                        try {
                            onDelta.accept(text);
                        } catch (Exception cbErr) {
                            log.warn("onDelta 回调异常", cbErr);
                        }
                    }
                    // tool_calls delta
                    JSONArray toolCallsDelta = delta.getJSONArray("tool_calls");
                    if (toolCallsDelta != null) {
                        for (int i = 0; i < toolCallsDelta.size(); i++) {
                            JSONObject tcd = toolCallsDelta.getJSONObject(i);
                            Integer idx = tcd.getInteger("index");
                            if (idx == null) idx = 0;
                            Map<String, Object> tc = sr.toolCalls.computeIfAbsent(idx, k -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("id", "");
                                m.put("type", "function");
                                Map<String, Object> f = new LinkedHashMap<>();
                                f.put("name", "");
                                f.put("arguments", "");
                                m.put("function", f);
                                return m;
                            });
                            String id = tcd.getString("id");
                            if (id != null) tc.put("id", id);
                            JSONObject fnDelta = tcd.getJSONObject("function");
                            if (fnDelta != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                                String nm = fnDelta.getString("name");
                                if (nm != null) fn.put("name", (String) fn.get("name") + nm);
                                String args = fnDelta.getString("arguments");
                                if (args != null) fn.put("arguments", (String) fn.get("arguments") + args);
                            }
                        }
                    }
                } catch (Exception parseErr) {
                    log.debug("SSE payload 解析失败: {}", payload);
                }
            }
        }
        return sr;
    }

    /**
     * 从一条 SSE data 行的 JSON payload 中提取 delta.content。
     * 兼容 OpenAI 标准格式（choices[0].delta.content）。
     */
    private String parseDeltaContent(String payload) {
        try {
            JSONObject obj = JSON.parseObject(payload);
            JSONArray choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            return delta.getString("content");
        } catch (Exception e) {
            log.debug("SSE payload 解析失败（可能是控制帧）: {}", payload);
            return null;
        }
    }

    /**
     * 抽取自 askChatGPT 的图片预处理逻辑，stream / 非 stream 路径共用。
     */
    private void preprocessImageContent(List<ChatGPTContent> chatContentList) {
        Iterator<ChatGPTContent> iterator = chatContentList.iterator();
        while (iterator.hasNext()) {
            ChatGPTContent chatGPTContent = iterator.next();
            if (chatGPTContent.getContent() instanceof List<?> contentList) {
                for (Object content : contentList) {
                    if (content instanceof Map imageMap) {
                        if (imageMap.get("type").equals("image_url")) {
                            Map imageUrlMap = (Map) imageMap.get("image_url");
                            String imageUrl = (String) imageUrlMap.get("url");
                            if (StringUtils.isNotBlank(imageUrl)) {
                                boolean isBase64 = imageUrl.startsWith("data:image/");
                                if (model.contains("moonshot") && !isBase64) {
                                    try (InputStream inputStream = restUtils.getFileInputStream(imageUrl);) {
                                        imageUrlMap.put("url", "data:image/png;base64," + FileUtils.InputStreamToBase64(inputStream));
                                    } catch (Exception e) {
                                        log.error("下载图片失败", e);
                                    }
                                } else if (!model.contains("moonshot") && isBase64) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!visionEnable) {
            chatContentList.forEach(chatGPTContent -> {
                if (chatGPTContent.getContent() instanceof List<?> contentList) {
                    contentList.removeIf(content -> content instanceof Map imageMap && imageMap.get("type").equals("image_url"));
                }
            });
        }
    }
}
