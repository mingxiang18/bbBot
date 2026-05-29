package com.bb.bot.api;

import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MessageContentVisitor} 单测：覆盖每种内容类型触发对应回调、空列表/未知类型/null 等边界。
 */
class MessageContentVisitorTest {

    /** 记录各回调收到的值，便于断言顺序与内容。 */
    private final List<String> texts = new ArrayList<>();
    private final List<File> localImages = new ArrayList<>();
    private final List<String> ats = new ArrayList<>();
    private final List<String> netImages = new ArrayList<>();

    private final Consumer<String> onText = texts::add;
    private final Consumer<File> onLocalImage = localImages::add;
    private final Consumer<String> onAt = ats::add;
    private final Consumer<String> onNetImage = netImages::add;

    private void visit(BbSendMessage msg) {
        MessageContentVisitor.forEachContent(msg, onText, onLocalImage, onAt, onNetImage);
    }

    private static BbSendMessage messageOf(BbMessageContent... contents) {
        BbSendMessage msg = new BbSendMessage();
        msg.setMessageList(new ArrayList<>(Arrays.asList(contents)));
        return msg;
    }

    private static BbMessageContent content(String type, Object data) {
        return BbMessageContent.builder().type(type).data(data).build();
    }

    @Test
    void textContent_triggersOnText() {
        visit(messageOf(BbMessageContent.buildTextContent("hello")));

        assertThat(texts).containsExactly("hello");
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void atContent_triggersOnAt() {
        visit(messageOf(BbMessageContent.buildAtMessageContent("user-123")));

        assertThat(ats).containsExactly("user-123");
        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void localImageContent_triggersOnLocalImage() {
        File file = new File("/tmp/some-image.png");
        visit(messageOf(BbMessageContent.buildLocalImageMessageContent(file)));

        assertThat(localImages).containsExactly(file);
        assertThat(texts).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void netImageContent_triggersOnNetImage() {
        visit(messageOf(BbMessageContent.buildNetImageMessageContent("http://img/x.png")));

        assertThat(netImages).containsExactly("http://img/x.png");
        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
    }

    @Test
    void mixedContent_triggersEachCallbackInOrder() {
        File file = new File("/tmp/a.png");
        visit(messageOf(
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent("t1"),
                BbMessageContent.buildLocalImageMessageContent(file),
                BbMessageContent.buildTextContent("t2"),
                BbMessageContent.buildNetImageMessageContent("http://n/1.png")
        ));

        assertThat(ats).containsExactly("u1");
        assertThat(texts).containsExactly("t1", "t2");
        assertThat(localImages).containsExactly(file);
        assertThat(netImages).containsExactly("http://n/1.png");
    }

    @Test
    void emptyList_triggersNothing() {
        visit(messageOf());

        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void nullMessageList_triggersNothing() {
        BbSendMessage msg = new BbSendMessage();
        msg.setMessageList(null);

        visit(msg);

        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void nullMessage_triggersNothing() {
        visit(null);

        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void unknownAndUntraversedTypes_triggerNothing() {
        // REPLY / LOCAL_FILE / NET_FILE / 完全未知类型都不在遍历职责内
        File file = new File("/tmp/doc.pdf");
        visit(messageOf(
                BbMessageContent.buildReplyMessageContent("msg-1"),
                BbMessageContent.buildLocalFileMessageContent(file),
                BbMessageContent.buildNetFileMessageContent("http://f/x.pdf", "x.pdf"),
                content("totallyUnknownType", "whatever")
        ));

        assertThat(texts).isEmpty();
        assertThat(localImages).isEmpty();
        assertThat(ats).isEmpty();
        assertThat(netImages).isEmpty();
    }

    @Test
    void nullDataOrNullType_isSkipped() {
        visit(messageOf(
                content(BbSendMessageType.TEXT, null),
                content(null, "orphan-data"),
                BbMessageContent.buildTextContent("kept")
        ));

        assertThat(texts).containsExactly("kept");
    }

    @Test
    void nullContentEntry_isSkipped() {
        visit(messageOf(
                null,
                BbMessageContent.buildTextContent("after-null")
        ));

        assertThat(texts).containsExactly("after-null");
    }

    @Test
    void localImageWithNonFileData_isSkipped() {
        // LOCAL_IMAGE 约定 data 为 File，类型不符时跳过避免 ClassCastException
        visit(messageOf(content(BbSendMessageType.LOCAL_IMAGE, "not-a-file")));

        assertThat(localImages).isEmpty();
        assertThat(texts).isEmpty();
    }

    @Test
    void nullCallbacks_areToleratedForUninterestedTypes() {
        // 渠道不关心某些类型时可不传回调；存在该类型也不应抛异常
        File file = new File("/tmp/a.png");
        MessageContentVisitor.forEachContent(
                messageOf(
                        BbMessageContent.buildTextContent("t"),
                        BbMessageContent.buildAtMessageContent("u"),
                        BbMessageContent.buildLocalImageMessageContent(file),
                        BbMessageContent.buildNetImageMessageContent("http://n")
                ),
                null, null, null, null);
        // 无回调即无副作用，不抛异常即通过
    }
}
