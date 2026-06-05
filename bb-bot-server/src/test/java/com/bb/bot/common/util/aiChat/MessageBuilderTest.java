package com.bb.bot.common.util.aiChat;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.MessageContent;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBuilderTest {

    @Test
    void buildContextMessages_includesPersonalityHistoryAndCurrent() {
        List<BbMessageContent> currentMsg = Collections.singletonList(
                BbMessageContent.buildTextContent("how are you"));
        List<ChatHistory> history = Arrays.asList(
                userHistory("alice", "hello"),
                botHistory("hi there"));

        List<ChatMessage> messages = MessageBuilder.buildContextMessages(
                "you are bb", currentMsg, history, null);

        // system + 2 history + current = 4
        assertEquals(4, messages.size());
        assertSame(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        assertEquals("you are bb", messages.get(0).getContents().get(0).getValue());

        assertSame(ChatMessage.Role.USER, messages.get(1).getRole());
        assertTrue(messages.get(1).getContents().get(0).getValue().contains("alice"));
        assertTrue(messages.get(1).getContents().get(0).getValue().contains("hello"));

        assertSame(ChatMessage.Role.ASSISTANT, messages.get(2).getRole());
        assertEquals("hi there", messages.get(2).getContents().get(0).getValue());

        assertSame(ChatMessage.Role.USER, messages.get(3).getRole());
    }

    @Test
    void buildContextMessages_skipsBlankPersonality() {
        List<BbMessageContent> currentMsg = Collections.singletonList(
                BbMessageContent.buildTextContent("hi"));
        List<ChatMessage> messages = MessageBuilder.buildContextMessages(
                "  ", currentMsg, Collections.emptyList(), null);

        assertEquals(1, messages.size());
        assertSame(ChatMessage.Role.USER, messages.get(0).getRole());
    }

    @Test
    void buildAskMessage_rendersImagesAsRefTextNotImageParts() {
        // 新设计：图片不再作为真实 image part 喂主模型，而是以「ref + 链接」文本进入单一 text part，
        // 由 AI 自行决定是否调 analyze_image 看图（选择性识图）。
        List<BbMessageContent> current = Arrays.asList(
                BbMessageContent.buildTextContent("look:"),
                BbMessageContent.builder().type(BbSendMessageType.NET_IMAGE)
                        .data("/img/aaa.png").fileName("aaa").build(),
                BbMessageContent.buildTextContent(" and"),
                BbMessageContent.builder()
                        .type(BbSendMessageType.LOCAL_IMAGE).data("BASE64DATA").build());

        ChatMessage ask = MessageBuilder.buildAskMessage(current, null);
        List<MessageContent> contents = ask.getContents();
        assertEquals(1, contents.size());
        assertEquals(MessageContent.Type.TEXT, contents.get(0).getType());
        String text = contents.get(0).getValue();
        assertTrue(text.contains("look:"));
        assertTrue(text.contains("and"));
        assertTrue(text.contains("图片 ref=aaa"));
        assertTrue(text.contains("/img/aaa.png"));
        assertTrue(text.contains("analyze_image"));
        // 不含任何真实图片 part
        assertFalse(contents.stream().anyMatch(MessageContent::isImage));
    }

    @Test
    void buildAskMessage_replyNetImageBecomesRefText() {
        ChatHistory replyTarget = new ChatHistory();
        replyTarget.setText(JSON.toJSONString(List.of(
                BbMessageContent.builder().type(BbSendMessageType.NET_IMAGE)
                        .data("/img/fromreply.png").fileName("fromreply").build(),
                BbMessageContent.buildTextContent("ignored"))));

        List<BbMessageContent> current = Collections.singletonList(BbMessageContent.buildTextContent("ok"));
        ChatMessage ask = MessageBuilder.buildAskMessage(current, replyTarget);

        List<MessageContent> contents = ask.getContents();
        assertEquals(1, contents.size());
        assertEquals(MessageContent.Type.TEXT, contents.get(0).getType());
        String text = contents.get(0).getValue();
        assertTrue(text.contains("图片 ref=fromreply"));
        assertTrue(text.contains("/img/fromreply.png"));
        assertTrue(text.contains("ok"));
        assertFalse(contents.stream().anyMatch(MessageContent::isImage));
    }

    @Test
    void historyToMessages_botBecomesAssistant_userBecomesUserWithNamePrefix() {
        List<ChatMessage> messages = MessageBuilder.historyToMessages(Arrays.asList(
                userHistory("alice", "morning"),
                botHistory("hi alice")));
        assertSame(ChatMessage.Role.USER, messages.get(0).getRole());
        assertTrue(messages.get(0).getContents().get(0).getValue().startsWith("alice："));
        assertSame(ChatMessage.Role.ASSISTANT, messages.get(1).getRole());
        assertEquals("hi alice", messages.get(1).getContents().get(0).getValue());
    }

    @Test
    void extractTextContent_keepsNetImageAsRef_filtersLocalImageAndReply_convertsAt() {
        ChatHistory hist = new ChatHistory();
        hist.setText(JSON.toJSONString(List.of(
                BbMessageContent.buildTextContent("hi"),
                BbMessageContent.buildAtMessageContent("alice"),
                BbMessageContent.builder().type(BbSendMessageType.NET_IMAGE)
                        .data("/img/abcdef.png").fileName("abcdef").build(),
                BbMessageContent.builder().type(BbSendMessageType.LOCAL_IMAGE).data("LOCALXX").build(),
                BbMessageContent.buildReplyMessageContent("rep999"))));
        String text = MessageBuilder.extractTextContent(hist);
        assertTrue(text.contains("hi"));
        assertTrue(text.contains("@alice"));
        // netImage（规范化后的 ref）保留成图片 ref，让 AI 看得到、可自主 analyze_image
        assertTrue(text.contains("图片 ref=abcdef"));
        assertTrue(text.contains("/img/abcdef.png"));
        // localImage（大 base64）与 reply 仍过滤
        assertFalse(text.contains("LOCALXX"));
        assertFalse(text.contains("rep999"));
    }

    @Test
    void extractTextContent_returnsRawWhenNotJson() {
        ChatHistory hist = new ChatHistory();
        hist.setText("plain string from old version");
        assertEquals("plain string from old version", MessageBuilder.extractTextContent(hist));
    }

    @Test
    void buildSummaryMessages_joinsAllHistoryAsSingleUserMessage() {
        List<ChatHistory> history = Arrays.asList(
                userHistory("alice", "msg1"),
                userHistory("bob", "msg2"),
                botHistory("response"));
        List<ChatMessage> messages = MessageBuilder.buildSummaryMessages("summarize", history);

        assertEquals(2, messages.size());
        assertSame(ChatMessage.Role.SYSTEM, messages.get(0).getRole());
        assertSame(ChatMessage.Role.USER, messages.get(1).getRole());
        String body = messages.get(1).getContents().get(0).getValue();
        assertTrue(body.contains("alice:msg1"));
        assertTrue(body.contains("bob:msg2"));
    }

    @Test
    void buildAskMessage_fileWithLocalPath_exposesPathAndFileReadHintToModel(@TempDir Path tmp) throws Exception {
        // AgentFileStore 落盘后，文件附件的 data 是一个存在的绝对路径
        Path saved = tmp.resolve("report.txt");
        Files.writeString(saved, "real content");
        List<BbMessageContent> current = Arrays.asList(
                BbMessageContent.buildTextContent("读一下"),
                BbMessageContent.builder()
                        .type(BbSendMessageType.LOCAL_FILE)
                        .data(saved.toAbsolutePath().toString())
                        .fileName("report.txt")
                        .build());

        ChatMessage ask = MessageBuilder.buildAskMessage(current, null);
        String text = ask.getContents().get(ask.getContents().size() - 1).getValue();
        assertTrue(text.contains("report.txt"));
        assertTrue(text.contains(saved.toAbsolutePath().toString()));
        assertTrue(text.contains("file_read"));
    }

    @Test
    void buildAskMessage_fileWithoutLocalPath_showsOnlyFileName() {
        // 未落盘的附件（data 仍是 URL / base64）只渲染文件名，不泄露 data
        List<BbMessageContent> current = Collections.singletonList(
                BbMessageContent.builder()
                        .type(BbSendMessageType.NET_FILE)
                        .data("https://example.com/x.pdf")
                        .fileName("x.pdf")
                        .build());

        ChatMessage ask = MessageBuilder.buildAskMessage(current, null);
        String text = ask.getContents().get(ask.getContents().size() - 1).getValue();
        assertTrue(text.contains("x.pdf"));
        assertFalse(text.contains("file_read"));
        assertFalse(text.contains("https://"));
    }

    private static ChatHistory userHistory(String name, String text) {
        ChatHistory h = new ChatHistory();
        h.setUserQq("u-" + name);
        h.setUserName(name);
        h.setText(text);
        h.setCreateTime(LocalDateTime.now());
        return h;
    }

    private static ChatHistory botHistory(String text) {
        ChatHistory h = new ChatHistory();
        h.setUserQq(MessageBuilder.BOT_USER_FLAG);
        h.setText(text);
        h.setCreateTime(LocalDateTime.now());
        return h;
    }
}
