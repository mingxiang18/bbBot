package com.bb.bot.handler.oneBot.aiChat;

import lombok.Data;

/**
 * chatGPT消息体
 * @author ren
 */
@Data
public class ChatGPTContent {

    public final static String USER_ROLE = "user";
    public final static String ASSISTANT_ROLE = "assistant";
    public final static String SYSTEM_ROLE = "system";

    /**
     * 角色
     * 已知：system-系统设定, user-用户，assistant-机器人回复
     * 默认值：user
     */
    private String role = USER_ROLE;

    /**
     * 具体消息内容
     */
    private String content;

    public ChatGPTContent() {}

    public ChatGPTContent(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatGPTContent(String content) {
        this.content = content;
    }
}
