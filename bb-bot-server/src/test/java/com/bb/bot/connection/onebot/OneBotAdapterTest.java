package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.api.oneBot.OneBotMessageApi;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.constant.BotType;
import com.bb.bot.constant.MessageType;
import com.bb.bot.dispatcher.InboundMessageDeduplicator;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** OneBot 协议转换、发送回执、鉴权与连接隔离的回归用例。 */
class OneBotAdapterTest {

    private OnebotConfig config() {
        OnebotConfig config = new OnebotConfig();
        config.setQq("10001");
        config.setApiTimeoutMs(100);
        return config;
    }

    private JSONObject event(Object message) {
        JSONObject event = new JSONObject();
        event.put("post_type", "message");
        event.put("message_type", "group");
        event.put("self_id", 10001);
        event.put("user_id", 20002);
        event.put("group_id", 30003);
        event.put("message_id", -123);
        event.put("message", message);
        return event;
    }

    @Test
    void receivesArraysWithoutLosingIdsConfigMentionsOrImages() {
        OnebotConfig config = config();
        String segments = """
                [
                  {"type":"at", "data":{"qq":"10001"}},
                  {"type":"text", "data":{"text":"a&amp;b"}},
                  {"type":"image", "data":{"file":"https://example.org/a.png"}},
                  {"type":"reply", "data":{"id":"-9"}}
                ]
                """;
        BbReceiveMessage message = OneBotMessageHandler.parseMessage(
                config, null, event(JSON.parseArray(segments)));
        assertSame(config, message.getConfig());
        assertEquals("-123", message.getMessageId());
        assertEquals("a&amp;b", message.getMessage());
        assertTrue(message.getAtUserList().get(0).getBotFlag());
        assertEquals(4, message.getMessageContentList().size());
        assertEquals("20002", message.getSender().getNickname());
    }

    @Test
    void receivesCqStringsAndUnescapesOnlyOnce() {
        BbReceiveMessage message =
                OneBotMessageHandler.parseMessage(
                        config(),
                        null,
                        event(
                                "[CQ:at,qq=10001]a&amp;b&#91;x&#93;[CQ:image,file=https://example.org/a&#44;b.png]"));
        assertEquals("a&b[x]", message.getMessage());
        assertTrue(message.getAtUserList().get(0).getBotFlag());
        assertEquals("https://example.org/a,b.png", message.getMessageContentList().get(2).getData());
        assertEquals(
                "&#91;",
                OneBotMessageHandler.parseMessage(config(), null, event("&amp;#91;")).getMessage());
    }

    @Test
    void ignoresSelfMessagesWrongAccountAndNonMessageEvents() {
        JSONObject event = event("text");
        event.put("user_id", 10001);
        assertNull(OneBotMessageHandler.parseMessage(config(), null, event));
        event.put("user_id", 20002);
        event.put("self_id", 999);
        assertNull(OneBotMessageHandler.parseMessage(config(), null, event));
        event.put("post_type", "notice");
        assertNull(OneBotMessageHandler.parseMessage(config(), null, event));
    }

    @Test
    void handlesPrivateMessagesWithoutSenderAndIgnoresMalformedFrames() {
        OnebotConfig config = config();
        JSONObject event = event("hello");
        event.put("message_type", "private");
        BbReceiveMessage message = OneBotMessageHandler.parseMessage(config, null, event);
        assertNull(message.getGroupId());
        assertEquals("20002", message.getSender().getUserId());
        assertDoesNotThrow(
                () ->
                        OneBotMessageHandler.handleMessage(
                                "test", config, null, "not json", publishedEvent -> fail()));
    }

    @Test
    void deduplicatesPerAccountAndConversationWhilePreservingRawMessageIds() {
        OnebotConfig config = config();
        InboundMessageDeduplicator deduplicator = new InboundMessageDeduplicator();
        BbReceiveMessage message = OneBotMessageHandler.parseMessage(config, null, event("text"));
        assertTrue(deduplicator.firstSeenMessage(message));
        assertFalse(deduplicator.firstSeenMessage(message));
        message.setGroupId("40004");
        assertTrue(deduplicator.firstSeenMessage(message));
        config.setQq("50005");
        assertTrue(deduplicator.firstSeenMessage(message));
        message.setBotType(BotType.QQ);
        assertTrue(deduplicator.firstSeenMessage(message));
        assertEquals("-123", message.getMessageId());
    }

    @Test
    void validatesBearerAndQueryTokenWithoutLoggingSecrets() {
        ClientHandshake handshake = mock(ClientHandshake.class);
        when(handshake.getFieldValue("Authorization")).thenReturn("Bearer secret");
        assertTrue(OneBotAuth.accepts("secret", handshake));
        assertFalse(OneBotAuth.accepts("wrong", handshake));
        when(handshake.getFieldValue("Authorization")).thenReturn("");
        when(handshake.getResourceDescriptor()).thenReturn("/?access_token=a%2Bb");
        assertTrue(OneBotAuth.accepts("a+b", handshake));
        assertFalse(
                OneBotAuth.endpoint(
                                java.net.URI.create(
                                        "ws://user:secret@localhost:3001/?access_token=secret"))
                        .contains("secret"));
        OnebotConfig config = config();
        config.setAccessToken("do-not-log");
        assertFalse(config.toString().contains("do-not-log"));
    }

    private WebSocket gateway(OnebotConfig config, int code, AtomicReference<JSONObject> sent) {
        WebSocket socket = mock(WebSocket.class);
        when(socket.isOpen()).thenReturn(true);
        config.getSession().bind(socket);
        doAnswer(
                        invocation -> {
                            JSONObject request = JSON.parseObject(invocation.getArgument(0, String.class));
                            sent.set(request);
                            JSONObject response = new JSONObject();
                            response.put("retcode", code);
                            response.put("echo", request.getString("echo"));
                            config.getSession().response(socket, response);
                            return null;
                        })
                .when(socket)
                .send(anyString());
        return socket;
    }

    @Test
    void groupRepliesUseGroupActionNumericTargetAndExplicitQuote() {
        OnebotConfig config = config();
        AtomicReference<JSONObject> sent = new AtomicReference<>();
        gateway(config, 0, sent);
        BbSendMessage message = new BbSendMessage();
        message.setConfig(config);
        message.setMessageType(MessageType.GROUP);
        message.setGroupId("30003");
        message.setMessageList(
                List.of(
                        BbMessageContent.buildReplyMessageContent("-123"),
                        BbMessageContent.buildTextContent("hello")));
        new OneBotMessageApi().sendMessage(message);
        assertEquals("send_group_msg", sent.get().getString("action"));
        assertEquals(30003L, sent.get().getJSONObject("params").getLongValue("group_id"));
        assertEquals(
                "reply",
                sent.get()
                        .getJSONObject("params")
                        .getJSONArray("message")
                        .getJSONObject(0)
                        .getString("type"));
    }

    @Test
    void rejectedSendsAndDisconnectedSendsThrowInsteadOfReportingSuccess() {
        OnebotConfig config = config();
        gateway(config, 1401, new AtomicReference<>());
        BbSendMessage message = new BbSendMessage();
        message.setConfig(config);
        message.setMessageType(MessageType.PRIVATE);
        message.setUserId("20002");
        message.setMessageList(List.of(BbMessageContent.buildTextContent("hello")));
        assertThrows(IllegalStateException.class, () -> new OneBotMessageApi().sendMessage(message));
        config.getSession().disconnected(config.getSession().connection());
        assertThrows(IllegalStateException.class, () -> new OneBotMessageApi().sendMessage(message));
    }

    @Test
    void privateRepliesUsePrivateActionAndLocalImagesAreTransferredAsBase64() throws Exception {
        OnebotConfig config = config();
        AtomicReference<JSONObject> sent = new AtomicReference<>();
        gateway(config, 0, sent);
        java.nio.file.Path file = java.nio.file.Files.createTempFile("onebot-image-", ".png");
        try {
            java.nio.file.Files.write(file, new byte[] {1, 2, 3});
            BbSendMessage message = new BbSendMessage();
            message.setConfig(config);
            message.setMessageType(MessageType.PRIVATE);
            message.setUserId("20002");
            message.setMessageList(
                    List.of(BbMessageContent.buildLocalImageMessageContent(file.toFile())));
            new OneBotMessageApi().sendMessage(message);
            assertEquals("send_private_msg", sent.get().getString("action"));
            assertEquals(
                    "base64://AQID",
                    sent.get()
                            .getJSONObject("params")
                            .getJSONArray("message")
                            .getJSONObject(0)
                            .getJSONObject("data")
                            .getString("file"));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    void timesOutWithoutRetryAndDoesNotAcceptResponsesFromAnotherSocket() {
        OnebotConfig config = config();
        WebSocket socket = mock(WebSocket.class);
        when(socket.isOpen()).thenReturn(true);
        config.getSession().bind(socket);
        doAnswer(
                        invocation -> {
                            JSONObject request = JSON.parseObject(invocation.getArgument(0, String.class));
                            JSONObject response = new JSONObject();
                            response.put("retcode", 0);
                            response.put("echo", request.getString("echo"));
                            config.getSession().response(mock(WebSocket.class), response);
                            return null;
                        })
                .when(socket)
                .send(anyString());
        assertThrows(
                IllegalStateException.class,
                () -> config.getSession().call("get_status", java.util.Map.of(), null, 100));
        verify(socket, times(1)).send(anyString());
    }

    @Test
    void synchronousBusinessReplyDoesNotBlockTheSocketReader() throws Exception {
        OnebotConfig config = config();
        WebSocket socket = mock(WebSocket.class);
        when(socket.isOpen()).thenReturn(true);
        config.getSession().bind(socket);
        AtomicReference<JSONObject> request = new AtomicReference<>();
        CountDownLatch sent = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            request.set(JSON.parseObject(invocation.getArgument(0, String.class)));
                            sent.countDown();
                            return null;
                        })
                .when(socket)
                .send(anyString());
        config.setApiTimeoutMs(3000);
        OneBotMessageHandler.handleMessage(
                "test",
                config,
                socket,
                event("hello").toJSONString(),
                msg -> {
                    try {
                        config.getSession()
                                .call(
                                        "send_group_msg",
                                        java.util.Map.of("group_id", 30003),
                                        null,
                                        3000);
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        done.countDown();
                    }
                });
        assertTrue(sent.await(2, TimeUnit.SECONDS));
        JSONObject response = new JSONObject();
        response.put("retcode", 0);
        response.put("echo", request.get().getString("echo"));
        OneBotMessageHandler.handleMessage("test", config, socket, response.toJSONString(), msg -> fail());
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertNull(failure.get());
        config.getSession().close();
    }
}
