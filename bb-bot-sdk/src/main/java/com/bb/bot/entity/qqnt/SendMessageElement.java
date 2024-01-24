package com.bb.bot.entity.qqnt;

import lombok.Builder;
import lombok.Data;

/**
 * 发送的消息元素
 * @author ren
 */
@Data
@Builder
public class SendMessageElement {
    /**
     * 消息类型 text-文本，image-图片
     */
    private String type;

    private String content;

    private String file;

    public static SendMessageElement buildTextMessage(String content) {
        return SendMessageElement.builder()
                .type("text")
                .content(content)
                .build();
    }

    public static SendMessageElement buildImgMessage(String file) {
        //要将路径C:\替换为C://的格式，还有所有右斜杠替换为左斜杠
        file = file.replace(":\\", "://").replace("\\", "/");
        return SendMessageElement.builder()
                .type("image")
                .file(file)
                .build();
    }
}
