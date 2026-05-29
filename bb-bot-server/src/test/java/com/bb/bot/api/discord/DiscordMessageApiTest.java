package com.bb.bot.api.discord;

import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DiscordMessageApi#collectTextContent} 重构为 {@code MessageContentVisitor.forEachContent}
 * 后的行为等价单测。
 *
 * <p>该私有方法负责把 {@link BbSendMessage} 的消息内容列表拼成 Discord 单条文本：文本原样拼接、
 * AT 拼成 {@code <@id> } mention、网络图片以换行 + URL 形式附在末尾、本地图片/文件不进文本
 * （走 sendFiles 上传）。本测试通过反射直接驱动该方法，断言拼接结果与重构前一致。</p>
 */
class DiscordMessageApiTest {

    private final DiscordMessageApi api = new DiscordMessageApi();

    private String collectTextContent(BbSendMessage message) throws Exception {
        Method method = DiscordMessageApi.class.getDeclaredMethod("collectTextContent", BbSendMessage.class);
        method.setAccessible(true);
        return (String) method.invoke(api, message);
    }

    private static BbMessageContent content(String type, Object data) {
        return BbMessageContent.builder().type(type).data(data).build();
    }

    private static BbSendMessage messageOf(BbMessageContent... contents) {
        BbSendMessage message = new BbSendMessage();
        message.setMessageList(new ArrayList<>(Arrays.asList(contents)));
        return message;
    }

    @Test
    void textIsAppendedAsIs() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.TEXT, "hello"),
                content(BbSendMessageType.TEXT, " world")));

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void atIsRenderedAsDiscordMention() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.AT, "12345")));

        assertThat(result).isEqualTo("<@12345> ");
    }

    @Test
    void netImageIsAppendedWithLeadingNewline() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.NET_IMAGE, "https://img/a.png")));

        assertThat(result).isEqualTo("\nhttps://img/a.png");
    }

    @Test
    void mixedContentPreservesOrderAndFormatting() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.AT, "u1"),
                content(BbSendMessageType.TEXT, "看图："),
                content(BbSendMessageType.NET_IMAGE, "https://img/b.png")));

        assertThat(result).isEqualTo("<@u1> 看图：\nhttps://img/b.png");
    }

    @Test
    void localImageAndOtherTypesAreExcludedFromText() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.TEXT, "before"),
                content(BbSendMessageType.LOCAL_IMAGE, new File("/tmp/whatever.png")),
                content(BbSendMessageType.LOCAL_FILE, new File("/tmp/whatever.txt")),
                content(BbSendMessageType.REPLY, "reply-id"),
                content(BbSendMessageType.TEXT, "after")));

        assertThat(result).isEqualTo("beforeafter");
    }

    @Test
    void nullDataIsSkipped() throws Exception {
        String result = collectTextContent(messageOf(
                content(BbSendMessageType.TEXT, null),
                content(BbSendMessageType.TEXT, "kept"),
                content(BbSendMessageType.AT, null),
                content(BbSendMessageType.NET_IMAGE, null)));

        assertThat(result).isEqualTo("kept");
    }

    @Test
    void emptyListProducesEmptyString() throws Exception {
        BbSendMessage message = new BbSendMessage();
        message.setMessageList(Collections.<BbMessageContent>emptyList());

        assertThat(collectTextContent(message)).isEmpty();
    }

    @Test
    void multipleAtAndNetImagesConcatInOrder() throws Exception {
        List<BbMessageContent> contents = new ArrayList<>();
        contents.add(content(BbSendMessageType.AT, "a"));
        contents.add(content(BbSendMessageType.AT, "b"));
        contents.add(content(BbSendMessageType.NET_IMAGE, "u1"));
        contents.add(content(BbSendMessageType.NET_IMAGE, "u2"));
        BbSendMessage message = new BbSendMessage();
        message.setMessageList(contents);

        assertThat(collectTextContent(message)).isEqualTo("<@a> <@b> \nu1\nu2");
    }
}
