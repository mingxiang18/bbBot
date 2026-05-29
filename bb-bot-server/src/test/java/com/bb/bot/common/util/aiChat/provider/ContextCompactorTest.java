package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 {@link ContextCompactor} 的压缩切点逻辑，重点是 tool 配对完整性
 * （suffix 不能以孤立 TOOL 消息开头）与 system 头保留。
 */
@ExtendWith(MockitoExtension.class)
class ContextCompactorTest {

    @Mock
    AiChatService aiChatService;

    @InjectMocks
    ContextCompactor compactor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(compactor, "thresholdChars", 200);
        ReflectionTestUtils.setField(compactor, "keepChars", 250);
    }

    @Test
    void underThreshold_returnsSameListUntouched() {
        List<ChatMessage> input = new ArrayList<>();
        input.add(ChatMessage.system("sys"));
        input.add(ChatMessage.user("hi"));
        input.add(ChatMessage.assistant("yo"));
        input.add(ChatMessage.user("bye"));

        List<ChatMessage> result = compactor.compactIfNeeded(input);

        assertSame(input, result, "未超阈值应原样返回");
    }

    @Test
    void overThreshold_compacts_keepsSystemHead_injectsSummary_andSuffixNotStartingWithTool() {
        when(aiChatService.chat(anyList(), any())).thenReturn("SUMMARY-TEXT");

        List<ChatMessage> input = buildLongConversationWithToolCalls();
        List<ChatMessage> result = compactor.compactIfNeeded(input);

        assertNotEquals(input.size(), result.size(), "应发生压缩");
        // system 头保留在最前
        assertEquals(ChatMessage.Role.SYSTEM, result.get(0).getRole());
        // 紧随其后是注入的摘要消息（user 角色）
        assertEquals(ChatMessage.Role.USER, result.get(1).getRole());
        assertTrue(textOf(result.get(1)).contains("SUMMARY-TEXT"), "摘要文本应被注入");
        // suffix 第一条不能是孤立 TOOL（否则 OpenAI/DeepSeek 会 400）
        assertNotEquals(ChatMessage.Role.TOOL, result.get(2).getRole(),
                "suffix 不能以孤立 TOOL 消息开头");
        // 整个结果里任何 TOOL 消息前面都应能找到带 toolCalls 的 assistant
        assertNoOrphanToolMessages(result);
        verify(aiChatService).chat(anyList(), any());
    }

    /** 构造一段超过阈值、含多组 assistant(tool_calls)+tool 的对话。 */
    private List<ChatMessage> buildLongConversationWithToolCalls() {
        String pad = "x".repeat(80);
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.system("system prompt " + pad));
        msgs.add(ChatMessage.user("用户问题一 " + pad));
        msgs.add(ChatMessage.assistantWithToolCalls("调用工具", List.of(
                ToolCall.builder().id("c1").name("read").argumentsJson("{}").build())));
        msgs.add(ChatMessage.tool("c1", "工具结果一 " + pad));
        msgs.add(ChatMessage.assistant("中间回答 " + pad));
        msgs.add(ChatMessage.user("用户问题二 " + pad));
        msgs.add(ChatMessage.assistantWithToolCalls("再调用", List.of(
                ToolCall.builder().id("c2").name("grep").argumentsJson("{}").build())));
        msgs.add(ChatMessage.tool("c2", "工具结果二 " + pad));
        msgs.add(ChatMessage.assistant("最终回答 " + pad));
        return msgs;
    }

    private void assertNoOrphanToolMessages(List<ChatMessage> messages) {
        boolean sawAssistantWithTools = false;
        for (ChatMessage m : messages) {
            if (m.getRole() == ChatMessage.Role.ASSISTANT
                    && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                sawAssistantWithTools = true;
            } else if (m.getRole() == ChatMessage.Role.TOOL) {
                assertTrue(sawAssistantWithTools,
                        "TOOL 消息前必须有带 tool_calls 的 assistant 消息");
            }
        }
    }

    private String textOf(ChatMessage m) {
        StringBuilder sb = new StringBuilder();
        for (MessageContent c : m.getContents()) {
            if (c.getType() == MessageContent.Type.TEXT && c.getValue() != null) {
                sb.append(c.getValue());
            }
        }
        return sb.toString();
    }
}
