package com.bb.bot.common.util;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.aiAgent.memory.MemoryEventRecorder;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * 验证 {@link BbReplies} 构造并发送的 {@link BbSendMessage} 结构正确，
 * 与各 handler 原内联回复逻辑等价。
 */
class BbRepliesTest {

    private BbMessageApi messageApi;
    private BbReplies replies;

    @BeforeEach
    void setUp() {
        messageApi = mock(BbMessageApi.class);
        replies = new BbReplies(messageApi);
    }

    private BbReceiveMessage receiveMessage() {
        BbReceiveMessage src = new BbReceiveMessage();
        src.setBotType("qq");
        src.setMessageType("group");
        src.setUserId("u-123");
        src.setGroupId("g-456");
        src.setMessageId("m-789");
        return src;
    }

    private BbSendMessage captureSent() {
        ArgumentCaptor<BbSendMessage> captor = ArgumentCaptor.forClass(BbSendMessage.class);
        verify(messageApi).sendMessage(captor.capture());
        return captor.getValue();
    }

    @Test
    void atText_should_build_at_then_text_two_segments() {
        BbReceiveMessage src = receiveMessage();

        replies.atText(src, "你好");

        BbSendMessage sent = captureSent();
        // 寻址信息透传自来源消息
        assertThat(sent.getBotType()).isEqualTo("qq");
        assertThat(sent.getMessageType()).isEqualTo("group");
        assertThat(sent.getUserId()).isEqualTo("u-123");
        assertThat(sent.getGroupId()).isEqualTo("g-456");
        assertThat(sent.getReceiveMessageId()).isEqualTo("m-789");

        List<BbMessageContent> list = sent.getMessageList();
        assertThat(list).hasSize(2);
        // 第一段：@发送者
        assertThat(list.get(0).getType()).isEqualTo(BbSendMessageType.AT);
        assertThat(list.get(0).getData()).isEqualTo("u-123");
        // 第二段：文本
        assertThat(list.get(1).getType()).isEqualTo(BbSendMessageType.TEXT);
        assertThat(list.get(1).getData()).isEqualTo("你好");
    }

    @Test
    void text_should_build_single_text_segment_without_at() {
        BbReceiveMessage src = receiveMessage();

        replies.text(src, "纯文本");

        BbSendMessage sent = captureSent();
        List<BbMessageContent> list = sent.getMessageList();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getType()).isEqualTo(BbSendMessageType.TEXT);
        assertThat(list.get(0).getData()).isEqualTo("纯文本");
    }

    @Test
    void text_should_passthrough_null_text() {
        replies.text(receiveMessage(), null);

        BbSendMessage sent = captureSent();
        assertThat(sent.getMessageList()).hasSize(1);
        assertThat(sent.getMessageList().get(0).getType()).isEqualTo(BbSendMessageType.TEXT);
        assertThat(sent.getMessageList().get(0).getData()).isNull();
    }

    @Test
    void text_should_passthrough_empty_text() {
        replies.text(receiveMessage(), "");

        BbSendMessage sent = captureSent();
        assertThat(sent.getMessageList()).hasSize(1);
        assertThat(sent.getMessageList().get(0).getType()).isEqualTo(BbSendMessageType.TEXT);
        assertThat(sent.getMessageList().get(0).getData()).isEqualTo("");
    }

    @Test
    void atText_should_passthrough_null_text() {
        replies.atText(receiveMessage(), null);

        BbSendMessage sent = captureSent();
        List<BbMessageContent> list = sent.getMessageList();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getType()).isEqualTo(BbSendMessageType.AT);
        assertThat(list.get(1).getType()).isEqualTo(BbSendMessageType.TEXT);
        assertThat(list.get(1).getData()).isNull();
    }

    @Test
    void send_should_passthrough_content_list_as_is() {
        BbReceiveMessage src = receiveMessage();
        BbMessageContent at = BbMessageContent.buildAtMessageContent("u-123");
        BbMessageContent text = BbMessageContent.buildTextContent("自定义");
        List<BbMessageContent> contents = List.of(at, text);

        replies.send(src, contents);

        BbSendMessage sent = captureSent();
        // 寻址信息透传
        assertThat(sent.getUserId()).isEqualTo("u-123");
        assertThat(sent.getReceiveMessageId()).isEqualTo("m-789");
        // 内容列表原样透传（同元素引用）
        assertThat(sent.getMessageList()).containsExactly(at, text);
    }

    @Test
    void send_should_accept_empty_list() {
        replies.send(receiveMessage(), Collections.emptyList());

        BbSendMessage sent = captureSent();
        assertThat(sent.getMessageList()).isEmpty();
    }

    @Test
    void sendWithMemoryKind_should_wrapSendWithRequestedKind() {
        MemoryEventRecorder recorder = mock(MemoryEventRecorder.class);
        setField(replies, "memoryEventRecorder", recorder);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(recorder).withOutboundKind(eq("chat_reply"), any(Runnable.class));

        replies.sendWithMemoryKind(receiveMessage(),
                List.of(BbMessageContent.buildTextContent("AI 回复")), "chat_reply");

        verify(recorder).withOutboundKind(eq("chat_reply"), any(Runnable.class));
        BbSendMessage sent = captureSent();
        assertThat(sent.getMessageList()).extracting(BbMessageContent::getData).containsExactly("AI 回复");
    }
}
