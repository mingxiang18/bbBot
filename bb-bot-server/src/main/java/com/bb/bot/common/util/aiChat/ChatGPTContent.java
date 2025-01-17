package com.bb.bot.common.util.aiChat;

import com.bb.bot.common.util.FileUtils;
import lombok.Data;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Object content;

    public ChatGPTContent() {}

    public ChatGPTContent(String role, String text) {
        this.role = role;
        this.content = text;
    }

    public ChatGPTContent(String role, List<?> content) {
        this.role = role;
        this.content = content;
    }

    public static Map<String, Object> buildTextContent(String text) {
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", text);
        return textContent;
    }

    public static Map<String, Object> buildImageContent(String imageUrl) {
        Map<String, Object> urlContent = new HashMap<>();
        urlContent.put("url", imageUrl);

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", urlContent);
        return imageContent;
    }

    public static Map<String, Object> buildImageContent(File image) {
        Map<String, Object> urlContent = new HashMap<>();
        urlContent.put("url", "data:image/png;base64," + FileUtils.fileToBase64(image));

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", urlContent);
        return imageContent;
    }
}
