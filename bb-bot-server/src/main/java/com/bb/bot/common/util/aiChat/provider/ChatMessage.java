package com.bb.bot.common.util.aiChat.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider-agnostic 消息体。content 可能是纯字符串或多段 part（含图片）。
 * contents 内部始终是可变 ArrayList，方便 provider 在发送前做模型相关的就地改写。
 *
 * <p>支持 function calling 协议：</p>
 * <ul>
 *   <li>{@code Role.ASSISTANT} 的消息可以带 {@link #toolCalls}（模型决定要调的工具列表）</li>
 *   <li>{@code Role.TOOL} 的消息必须带 {@link #toolCallId}（关联 assistant 那条 tool_calls 的 id）</li>
 * </ul>
 *
 * @author ren
 */
@Getter
public class ChatMessage {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    private final Role role;
    private final List<MessageContent> contents;

    /** assistant 消息：本轮模型决定要调的工具（function calling）。其它角色为 null。 */
    @Setter
    private List<ToolCall> toolCalls;

    /** tool 消息：本条工具结果对应哪个 tool_call.id。其它角色为 null。 */
    @Setter
    private String toolCallId;

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

    /**
     * Assistant 消息，附带本轮模型决定的工具调用列表。
     * text 可空（纯工具调用时模型可能不输出文本）。
     */
    public static ChatMessage assistantWithToolCalls(String text, List<ToolCall> toolCalls) {
        List<MessageContent> parts = (text == null || text.isEmpty())
                ? Collections.emptyList()
                : List.of(MessageContent.text(text));
        ChatMessage m = new ChatMessage(Role.ASSISTANT, parts);
        m.setToolCalls(toolCalls);
        return m;
    }

    /**
     * Tool 消息：把一次工具执行的结果回灌给模型。
     *
     * @param toolCallId  关联的 assistant tool_call.id
     * @param resultText  工具执行结果的字符串表示（一般是 JSON 字符串）
     */
    public static ChatMessage tool(String toolCallId, String resultText) {
        ChatMessage m = new ChatMessage(Role.TOOL, List.of(MessageContent.text(resultText == null ? "" : resultText)));
        m.setToolCallId(toolCallId);
        return m;
    }

    /** 是否包含图像 part。 */
    public boolean hasImage() {
        return contents.stream().anyMatch(MessageContent::isImage);
    }
}
