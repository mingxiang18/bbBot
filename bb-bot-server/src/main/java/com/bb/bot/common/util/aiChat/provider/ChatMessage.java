package com.bb.bot.common.util.aiChat.provider;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider-agnostic 消息体。content 可能是纯字符串或多段 part（含图片）。
 * contents 内部始终是可变 ArrayList，方便 provider 在发送前做模型相关的就地改写。
 *
 * @author ren
 */
@Getter
public class ChatMessage {

    public enum Role { SYSTEM, USER, ASSISTANT }

    private final Role role;
    private final List<MessageContent> contents;

    public ChatMessage(Role role, List<MessageContent> contents) {
        this.role = role;
        this.contents = new ArrayList<>(contents == null ? Collections.emptyList() : contents);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(Role.SYSTEM, List.of(MessageContent.text(text)));
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, List.of(MessageContent.text(text)));
    }

    public static ChatMessage user(List<MessageContent> contents) {
        return new ChatMessage(Role.USER, contents);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, List.of(MessageContent.text(text)));
    }

    /** 是否包含图像 part。 */
    public boolean hasImage() {
        return contents.stream().anyMatch(MessageContent::isImage);
    }
}
