package com.bb.bot.handler.oneBot.aiChat;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * chatGPT请求类
 * @author ren
 */
@Data
public class ChatGPTRequest {

    /**
     * 模型
     * 默认使用3.5
     */
    private String model = "gpt-3.5-turbo";

    /**
     * 消息
     */
    private List<ChatGPTContent> messages;

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
