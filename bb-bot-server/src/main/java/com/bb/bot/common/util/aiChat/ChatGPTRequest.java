package com.bb.bot.common.util.aiChat;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * chatGPT请求类。
 *
 * <p>fastjson2 默认序列化时跳过 null 字段，所以未设置的字段（stream / tools / tool_choice）
 * 不会出现在 JSON 中，与旧请求兼容。</p>
 *
 * @author ren
 */
@Data
public class ChatGPTRequest {

    /**
     * 模型
     */
    private String model;

    /**
     * 消息
     */
    private List<ChatGPTContent> messages;

    /**
     * 是否启用 SSE 流式响应。null = 不开启（兼容旧请求）。
     */
    private Boolean stream;

    /**
     * 工具列表（OpenAI function calling 协议）。M2 工具调用阶段使用。
     */
    private List<Object> tools;

    /**
     * 工具选择策略。"auto" / "none" / {"type":"function","function":{"name":"..."}}
     */
    private Object tool_choice;

    public ChatGPTRequest() {}

    public ChatGPTRequest(String model, List<ChatGPTContent> messages) {
        this.model = model;
        this.messages = messages;
    }

    public ChatGPTRequest(List<ChatGPTContent> messages) {
        this.messages = messages;
    }

    public ChatGPTRequest(ChatGPTContent messages) {
        this.messages = Arrays.asList(messages);
    }
}
