package com.bb.bot.api.qq;

import com.bb.bot.common.util.fileClient.FileClientApi;
import com.bb.bot.config.QqConfig;
import com.bb.bot.connection.qq.QqApiCaller;
import com.bb.bot.constant.MessageType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.entity.qq.ChannelMessage;
import com.bb.bot.entity.qq.GroupMessage;
import com.bb.bot.entity.qq.UploadMediaRequest;
import com.bb.bot.entity.qq.UploadMediaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link QqToBbMessageApi} 四个发送方法重构为 {@code MessageContentVisitor.forEachContent} 后的行为等价单测。
 *
 * <p>覆盖四渠道（群/子频道/C2C 单聊/频道私信）对各内容类型（文本拼接 / 本地图片上传 / 网络图片 / AT）的处理：
 * 文本按列表顺序拼接、本地/网络图片走对应渠道的 media 上传或 image 字段、AT 仅在频道/私信生效、群/单聊忽略 AT。</p>
 */
@ExtendWith(MockitoExtension.class)
class QqToBbMessageApiTest {

    @Mock
    private QqApiCaller qqApiCaller;

    @Mock
    private FileClientApi fileClientApi;

    @InjectMocks
    private QqToBbMessageApi api;

    private QqConfig qqConfig;

    /**
     * 每个测试用唯一的被动消息 id，避开 {@link QqToBbMessageApi} 内 static msg_seq 缓存的跨用例残留，
     * 保证单条发送的 msg_seq 恒为 1（不受其它用例执行顺序影响）。
     */
    private String receiveId;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        qqConfig = new QqConfig();
        receiveId = "recv-" + System.nanoTime();
    }

    private File newLocalImage() throws Exception {
        Path p = tempDir.resolve("img-" + System.nanoTime() + ".png");
        Files.write(p, new byte[]{1, 2, 3});
        return p.toFile();
    }

    private BbSendMessage baseMessage(String messageType) {
        BbSendMessage msg = new BbSendMessage();
        msg.setMessageType(messageType);
        msg.setReceiveMessageId(receiveId);
        msg.setGroupId("g-openid");
        msg.setUserId("u-openid");
        msg.setMessageList(new ArrayList<>());
        return msg;
    }

    private BbSendMessage messageOf(String messageType, BbMessageContent... contents) {
        BbSendMessage msg = baseMessage(messageType);
        msg.setMessageList(new ArrayList<>(Arrays.asList(contents)));
        msg.setConfig(qqConfig);
        return msg;
    }

    // ---------- 空列表：直接返回，不触碰任何渠道接口 ----------

    @Test
    void emptyMessageList_sendsNothing() {
        BbSendMessage msg = baseMessage(MessageType.GROUP);
        msg.setConfig(qqConfig);

        api.sendMessage(msg);

        verifyNoInteractions(qqApiCaller, fileClientApi);
    }

    // ---------- 群消息 ----------

    @Test
    void group_textConcatenatedInOrder() {
        BbSendMessage msg = messageOf(MessageType.GROUP,
                BbMessageContent.buildTextContent("hello "),
                BbMessageContent.buildTextContent("world"));

        api.sendMessage(msg);

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendGroupMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        GroupMessage sent = captor.getValue();
        assertThat(sent.getContent()).isEqualTo("hello world");
        assertThat(sent.getMsgType()).isEqualTo(0);
        assertThat(sent.getMedia()).isNull();
        assertThat(sent.getMsgId()).isEqualTo(receiveId);
        assertThat(sent.getMsgSeq()).isEqualTo("1");
        verify(qqApiCaller, never()).uploadGroupMedia(any(), any(), any());
    }

    @Test
    void group_localImage_uploadsAndSetsMedia() throws Exception {
        UploadMediaResponse resp = new UploadMediaResponse();
        when(fileClientApi.uploadTmpFile(any(InputStream.class))).thenReturn("http://tmp/local.png");
        when(qqApiCaller.uploadGroupMedia(eq(qqConfig), eq("g-openid"), any(UploadMediaRequest.class))).thenReturn(resp);

        BbSendMessage msg = messageOf(MessageType.GROUP,
                BbMessageContent.buildLocalImageMessageContent(newLocalImage()));

        api.sendMessage(msg);

        ArgumentCaptor<UploadMediaRequest> reqCaptor = ArgumentCaptor.forClass(UploadMediaRequest.class);
        verify(qqApiCaller).uploadGroupMedia(eq(qqConfig), eq("g-openid"), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getUrl()).isEqualTo("http://tmp/local.png");
        assertThat(reqCaptor.getValue().getFileType()).isEqualTo(1);

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendGroupMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getMsgType()).isEqualTo(7);
        assertThat(captor.getValue().getMedia()).isSameAs(resp);
        assertThat(captor.getValue().getContent()).isEmpty();
    }

    @Test
    void group_netImage_uploadsByUrlAndSetsMedia() {
        UploadMediaResponse resp = new UploadMediaResponse();
        when(qqApiCaller.uploadGroupMedia(eq(qqConfig), eq("g-openid"), any(UploadMediaRequest.class))).thenReturn(resp);

        BbSendMessage msg = messageOf(MessageType.GROUP,
                BbMessageContent.buildNetImageMessageContent("http://net/x.png"));

        api.sendMessage(msg);

        ArgumentCaptor<UploadMediaRequest> reqCaptor = ArgumentCaptor.forClass(UploadMediaRequest.class);
        verify(qqApiCaller).uploadGroupMedia(eq(qqConfig), eq("g-openid"), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getUrl()).isEqualTo("http://net/x.png");

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendGroupMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getMsgType()).isEqualTo(7);
        assertThat(captor.getValue().getMedia()).isSameAs(resp);
        verifyNoInteractions(fileClientApi);
    }

    @Test
    void group_atIsIgnored() {
        BbSendMessage msg = messageOf(MessageType.GROUP,
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent("hi"));

        api.sendMessage(msg);

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendGroupMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        // 群消息不处理 AT，仅保留文本
        assertThat(captor.getValue().getContent()).isEqualTo("hi");
    }

    @Test
    void group_msgSeqIncrementsPerReceiveId() {
        api.sendMessage(messageOf(MessageType.GROUP, BbMessageContent.buildTextContent("a")));
        api.sendMessage(messageOf(MessageType.GROUP, BbMessageContent.buildTextContent("b")));

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller, org.mockito.Mockito.times(2))
                .sendGroupMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getAllValues().get(0).getMsgSeq()).isEqualTo("1");
        assertThat(captor.getAllValues().get(1).getMsgSeq()).isEqualTo("2");
    }

    // ---------- 子频道消息 ----------

    @Test
    void channel_atAndTextConcatenatedInOrder() {
        BbSendMessage msg = messageOf(MessageType.CHANNEL,
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent(" hi"));

        api.sendMessage(msg);

        ArgumentCaptor<ChannelMessage> captor = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(qqApiCaller).sendChannelMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("<@u1> hi");
        assertThat(captor.getValue().getMsgId()).isEqualTo(receiveId);
        assertThat(captor.getValue().getImage()).isNull();
    }

    @Test
    void channel_localImage_setsImageFromUpload() throws Exception {
        when(fileClientApi.uploadTmpFile(any(InputStream.class))).thenReturn("http://tmp/c.png");

        BbSendMessage msg = messageOf(MessageType.CHANNEL,
                BbMessageContent.buildLocalImageMessageContent(newLocalImage()));

        api.sendMessage(msg);

        ArgumentCaptor<ChannelMessage> captor = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(qqApiCaller).sendChannelMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getImage()).isEqualTo("http://tmp/c.png");
    }

    @Test
    void channel_netImage_setsImageDirectly() {
        BbSendMessage msg = messageOf(MessageType.CHANNEL,
                BbMessageContent.buildNetImageMessageContent("http://net/c.png"));

        api.sendMessage(msg);

        ArgumentCaptor<ChannelMessage> captor = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(qqApiCaller).sendChannelMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getImage()).isEqualTo("http://net/c.png");
        verifyNoInteractions(fileClientApi);
    }

    // ---------- C2C 单聊（groupId 为空，走 sendC2CMessage） ----------

    @Test
    void c2c_textAndLocalImage() throws Exception {
        UploadMediaResponse resp = new UploadMediaResponse();
        when(fileClientApi.uploadTmpFile(any(InputStream.class))).thenReturn("http://tmp/p.png");
        when(qqApiCaller.uploadC2CMedia(eq(qqConfig), eq("u-openid"), any(UploadMediaRequest.class))).thenReturn(resp);

        BbSendMessage msg = baseMessage(MessageType.PRIVATE);
        msg.setGroupId(null); // 无 guild_id => C2C
        msg.setConfig(qqConfig);
        msg.setMessageList(new ArrayList<>(Arrays.asList(
                BbMessageContent.buildTextContent("pm"),
                BbMessageContent.buildLocalImageMessageContent(newLocalImage()))));

        api.sendMessage(msg);

        ArgumentCaptor<UploadMediaRequest> reqCaptor = ArgumentCaptor.forClass(UploadMediaRequest.class);
        verify(qqApiCaller).uploadC2CMedia(eq(qqConfig), eq("u-openid"), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getUrl()).isEqualTo("http://tmp/p.png");

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendC2CMessage(eq(qqConfig), eq("u-openid"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("pm");
        assertThat(captor.getValue().getMsgType()).isEqualTo(7);
        assertThat(captor.getValue().getMedia()).isSameAs(resp);
        assertThat(captor.getValue().getMsgSeq()).isEqualTo("1");
    }

    @Test
    void c2c_netImage_uploadsByUrl() {
        UploadMediaResponse resp = new UploadMediaResponse();
        when(qqApiCaller.uploadC2CMedia(eq(qqConfig), eq("u-openid"), any(UploadMediaRequest.class))).thenReturn(resp);

        BbSendMessage msg = baseMessage(MessageType.PRIVATE);
        msg.setGroupId(null);
        msg.setConfig(qqConfig);
        msg.setMessageList(new ArrayList<>(Arrays.asList(
                BbMessageContent.buildNetImageMessageContent("http://net/p.png"))));

        api.sendMessage(msg);

        ArgumentCaptor<UploadMediaRequest> reqCaptor = ArgumentCaptor.forClass(UploadMediaRequest.class);
        verify(qqApiCaller).uploadC2CMedia(eq(qqConfig), eq("u-openid"), reqCaptor.capture());
        assertThat(reqCaptor.getValue().getUrl()).isEqualTo("http://net/p.png");

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendC2CMessage(eq(qqConfig), eq("u-openid"), captor.capture());
        assertThat(captor.getValue().getMsgType()).isEqualTo(7);
        assertThat(captor.getValue().getMedia()).isSameAs(resp);
    }

    @Test
    void c2c_atIsIgnored() {
        BbSendMessage msg = baseMessage(MessageType.PRIVATE);
        msg.setGroupId(null);
        msg.setConfig(qqConfig);
        msg.setMessageList(new ArrayList<>(Arrays.asList(
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent("yo"))));

        api.sendMessage(msg);

        ArgumentCaptor<GroupMessage> captor = ArgumentCaptor.forClass(GroupMessage.class);
        verify(qqApiCaller).sendC2CMessage(eq(qqConfig), eq("u-openid"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("yo");
    }

    // ---------- 频道私信（PRIVATE + 非空 groupId => sendDirectMessage） ----------

    @Test
    void direct_atTextAndNetImage() {
        BbSendMessage msg = baseMessage(MessageType.PRIVATE);
        // groupId 非空 => 频道私信
        msg.setConfig(qqConfig);
        msg.setMessageList(new ArrayList<>(Arrays.asList(
                BbMessageContent.buildAtMessageContent("u1"),
                BbMessageContent.buildTextContent(" dm"),
                BbMessageContent.buildNetImageMessageContent("http://net/d.png"))));

        api.sendMessage(msg);

        ArgumentCaptor<ChannelMessage> captor = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(qqApiCaller).sendDirectMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("<@u1> dm");
        assertThat(captor.getValue().getImage()).isEqualTo("http://net/d.png");
        assertThat(captor.getValue().getMsgId()).isEqualTo(receiveId);
    }

    @Test
    void direct_localImage_setsImageFromUpload() throws Exception {
        when(fileClientApi.uploadTmpFile(any(InputStream.class))).thenReturn("http://tmp/d.png");

        BbSendMessage msg = baseMessage(MessageType.PRIVATE);
        msg.setConfig(qqConfig);
        msg.setMessageList(new ArrayList<>(Arrays.asList(
                BbMessageContent.buildLocalImageMessageContent(newLocalImage()))));

        api.sendMessage(msg);

        ArgumentCaptor<ChannelMessage> captor = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(qqApiCaller).sendDirectMessage(eq(qqConfig), eq("g-openid"), captor.capture());
        assertThat(captor.getValue().getImage()).isEqualTo("http://tmp/d.png");
    }
}
