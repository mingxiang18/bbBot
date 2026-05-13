package com.bb.bot.common.util.aiChat.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消息内容片段：纯文本，或图片（网络 URL / base64）。
 * 与 OpenAI Chat Completion 的 content part 结构对齐，便于直接序列化。
 *
 * @author ren
 */
@Getter
@RequiredArgsConstructor
public class MessageContent {

    public enum Type { TEXT, NET_IMAGE, BASE64_IMAGE }

    private final Type type;
    private final String value;

    public static MessageContent text(String text) {
        return new MessageContent(Type.TEXT, text);
    }

    public static MessageContent netImage(String imageUrl) {
        return new MessageContent(Type.NET_IMAGE, imageUrl);
    }

    public static MessageContent base64Image(String base64) {
        return new MessageContent(Type.BASE64_IMAGE, base64);
    }

    public boolean isImage() {
        return type == Type.NET_IMAGE || type == Type.BASE64_IMAGE;
    }
}
