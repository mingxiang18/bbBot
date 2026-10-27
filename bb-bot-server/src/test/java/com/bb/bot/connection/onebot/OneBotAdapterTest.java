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

class OneBotAdapterTest {
    private OnebotConfig config() {
        OnebotConfig c = new OnebotConfig(); c.setQq("10001"); c.setApiTimeoutMs(100); return c;
    }
    private JSONObject event(Object message) {
        JSONObject e = new JSONObject();
        e.put("post_type", "message"); e.put("message_type", "group"); e.put("self_id", 10001);
        e.put("user_id", 20002); e.put("group_id", 30003); e.put("message_id", -123); e.put("message", message);
        return e;
    }
    @Test void receivesArraysWithoutLosingIdsConfigMentionsOrImages() {
        OnebotConfig c = config();
        BbReceiveMessage m = OneBotMessageHandler.parseMessage(c, null, event(JSON.parseArray("[{\"type\":\"at\",\"data\":{\"qq\":\"10001\"}},{\"type\":\"text\",\"data\":{\"text\":\"a&amp;b\"}},{\"type\":\"image\",\"data\":{\"file\":\"https://example.org/a.png\"}},{\"type\":\"reply\",\"data\":{\"id\":\"-9\"}}]")));
        assertSame(c,m.getConfig()); assertEquals("-123",m.getMessageId());
        assertEquals("a&amp;b",m.getMessage()); assertTrue(m.getAtUserList().get(0).getBotFlag());
        assertEquals(4,m.getMessageContentList().size()); assertEquals("20002",m.getSender().getNickname());
    }
    @Test void receivesCqStringsAndUnescapesOnlyOnce() {
        BbReceiveMessage m=OneBotMessageHandler.parseMessage(config(),null,event("[CQ:at,qq=10001]a&amp;b&#91;x&#93;[CQ:image,file=https://example.org/a&#44;b.png]"));
        assertEquals("a&b[x]",m.getMessage()); assertTrue(m.getAtUserList().get(0).getBotFlag());
        assertEquals("https://example.org/a,b.png",m.getMessageContentList().get(2).getData());
        assertEquals("&#91;",OneBotMessageHandler.parseMessage(config(),null,event("&amp;#91;")).getMessage());
    }
    @Test void ignoresSelfMessagesWrongAccountAndNonMessageEvents() {
        JSONObject e=event("text"); e.put("user_id",10001);
        assertNull(OneBotMessageHandler.parseMessage(config(),null,e));
        e.put("user_id",20002); e.put("self_id",999);
        assertNull(OneBotMessageHandler.parseMessage(config(),null,e));
        e.put("post_type","notice"); assertNull(OneBotMessageHandler.parseMessage(config(),null,e));
    }
    @Test void handlesPrivateMessagesWithoutSenderAndIgnoresMalformedFrames() {
        OnebotConfig c=config(); JSONObject e=event("hello"); e.put("message_type","private");
        BbReceiveMessage m=OneBotMessageHandler.parseMessage(c,null,e);
        assertNull(m.getGroupId()); assertEquals("20002",m.getSender().getUserId());
        assertDoesNotThrow(()->OneBotMessageHandler.handleMessage("test",c,null,"not json",message->fail()));
    }
    @Test void deduplicatesPerAccountAndConversationWhilePreservingRawMessageIds() {
        OnebotConfig c=config(); InboundMessageDeduplicator d=new InboundMessageDeduplicator();
        BbReceiveMessage m=OneBotMessageHandler.parseMessage(c,null,event("text"));
        assertTrue(d.firstSeenMessage(m)); assertFalse(d.firstSeenMessage(m));
        m.setGroupId("40004"); assertTrue(d.firstSeenMessage(m));
        c.setQq("50005"); assertTrue(d.firstSeenMessage(m));
        m.setBotType(BotType.QQ); assertTrue(d.firstSeenMessage(m)); assertEquals("-123",m.getMessageId());
    }
    @Test void validatesBearerAndQueryTokenWithoutLoggingSecrets() {
        ClientHandshake h=mock(ClientHandshake.class);
        when(h.getFieldValue("Authorization")).thenReturn("Bearer secret");
        assertTrue(OneBotAuth.accepts("secret",h)); assertFalse(OneBotAuth.accepts("wrong",h));
        when(h.getFieldValue("Authorization")).thenReturn("");
        when(h.getResourceDescriptor()).thenReturn("/?access_token=a%2Bb");
        assertTrue(OneBotAuth.accepts("a+b",h));
        assertFalse(OneBotAuth.endpoint(java.net.URI.create("ws://user:secret@localhost:3001/?access_token=secret")).contains("secret"));
        OnebotConfig c=config(); c.setAccessToken("do-not-log"); assertFalse(c.toString().contains("do-not-log"));
    }
    private WebSocket gateway(OnebotConfig c,int code,AtomicReference<JSONObject> sent) {
        WebSocket ws=mock(WebSocket.class); when(ws.isOpen()).thenReturn(true); c.getSession().bind(ws);
        doAnswer(inv->{
            JSONObject request=JSON.parseObject(inv.getArgument(0,String.class)); sent.set(request);
            JSONObject response=new JSONObject(); response.put("retcode",code); response.put("echo",request.getString("echo"));
            c.getSession().response(ws,response); return null;
        }).when(ws).send(anyString()); return ws;
    }
    @Test void groupRepliesUseGroupActionNumericTargetAndExplicitQuote() {
        OnebotConfig c=config(); AtomicReference<JSONObject> sent=new AtomicReference<>(); gateway(c,0,sent);
        BbSendMessage m=new BbSendMessage(); m.setConfig(c); m.setMessageType(MessageType.GROUP); m.setGroupId("30003");
        m.setMessageList(List.of(BbMessageContent.buildReplyMessageContent("-123"),BbMessageContent.buildTextContent("hello")));
        new OneBotMessageApi().sendMessage(m);
        assertEquals("send_group_msg",sent.get().getString("action"));
        assertEquals(30003L,sent.get().getJSONObject("params").getLongValue("group_id"));
        assertEquals("reply",sent.get().getJSONObject("params").getJSONArray("message").getJSONObject(0).getString("type"));
    }
    @Test void rejectedSendsAndDisconnectedSendsThrowInsteadOfReportingSuccess() {
        OnebotConfig c=config(); gateway(c,1401,new AtomicReference<>());
        BbSendMessage m=new BbSendMessage(); m.setConfig(c); m.setMessageType(MessageType.PRIVATE); m.setUserId("20002");
        m.setMessageList(List.of(BbMessageContent.buildTextContent("hello")));
        assertThrows(IllegalStateException.class,()->new OneBotMessageApi().sendMessage(m));
        c.getSession().disconnected(c.getSession().connection());
        assertThrows(IllegalStateException.class,()->new OneBotMessageApi().sendMessage(m));
    }
    @Test void privateRepliesUsePrivateActionAndLocalImagesAreTransferredAsBase64() throws Exception {
        OnebotConfig c=config(); AtomicReference<JSONObject> sent=new AtomicReference<>(); gateway(c,0,sent);
        java.nio.file.Path file=java.nio.file.Files.createTempFile("onebot-image-", ".png");
        try {
            java.nio.file.Files.write(file,new byte[]{1,2,3});
            BbSendMessage m=new BbSendMessage(); m.setConfig(c); m.setMessageType(MessageType.PRIVATE);m.setUserId("20002");
            m.setMessageList(List.of(BbMessageContent.buildLocalImageMessageContent(file.toFile())));
            new OneBotMessageApi().sendMessage(m);
            assertEquals("send_private_msg",sent.get().getString("action"));
            assertEquals("base64://AQID",sent.get().getJSONObject("params").getJSONArray("message").getJSONObject(0).getJSONObject("data").getString("file"));
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }
    @Test void timesOutWithoutRetryAndDoesNotAcceptResponsesFromAnotherSocket() {
        OnebotConfig c=config(); WebSocket ws=mock(WebSocket.class); when(ws.isOpen()).thenReturn(true); c.getSession().bind(ws);
        doAnswer(inv->{JSONObject req=JSON.parseObject(inv.getArgument(0,String.class));
            JSONObject response=new JSONObject(); response.put("retcode",0);response.put("echo",req.getString("echo"));
            c.getSession().response(mock(WebSocket.class),response);return null;
        }).when(ws).send(anyString());
        assertThrows(IllegalStateException.class,()->c.getSession().call("get_status",java.util.Map.of(),null,100));
        verify(ws,times(1)).send(anyString());
    }
    @Test void synchronousBusinessReplyDoesNotBlockTheSocketReader() throws Exception {
        OnebotConfig c=config(); WebSocket ws=mock(WebSocket.class); when(ws.isOpen()).thenReturn(true); c.getSession().bind(ws);
        AtomicReference<JSONObject> request=new AtomicReference<>(); CountDownLatch sent=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        doAnswer(inv->{request.set(JSON.parseObject(inv.getArgument(0,String.class)));sent.countDown();return null;}).when(ws).send(anyString());
        c.setApiTimeoutMs(3000);
        OneBotMessageHandler.handleMessage("test",c,ws,event("hello").toJSONString(),msg->{
            try { c.getSession().call("send_group_msg",java.util.Map.of("group_id",30003),null,3000); }
            catch (Throwable e) { failure.set(e); }
            finally { done.countDown(); }
        });
        assertTrue(sent.await(2,TimeUnit.SECONDS));
        JSONObject response=new JSONObject(); response.put("retcode",0);response.put("echo",request.get().getString("echo"));
        OneBotMessageHandler.handleMessage("test",c,ws,response.toJSONString(),msg->fail());
        assertTrue(done.await(2,TimeUnit.SECONDS)); assertNull(failure.get()); c.getSession().close();
    }
}
