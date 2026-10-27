package com.bb.bot.connection.onebot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.api.oneBot.OneBotMessageApi;
import com.bb.bot.config.OnebotConfig;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OneBotWebSocketIntegrationTest {
    private WebSocketClient gateway(int port,String token,AtomicReference<JSONObject> received,CountDownLatch replied) {
        return new WebSocketClient(URI.create("ws://127.0.0.1:"+port),Map.of("Authorization","Bearer "+token,"X-Self-ID","10001")) {
            public void onOpen(ServerHandshake handshake) { }
            public void onClose(int code,String reason,boolean remote) { }
            public void onError(Exception error) { }
            public void onMessage(String text) {
                JSONObject request=JSON.parseObject(text);received.set(request);
                send(JSON.toJSONString(Map.of("status","ok","retcode",0,"echo",request.getString("echo"),"data",Map.of("message_id",-456))));
                replied.countDown();
            }
        };
    }
    @Test void authenticatedReverseWebSocketRunsSynchronousHandlerAndReceivesApiAcknowledgement() throws Exception {
        OnebotConfig c=new OnebotConfig();c.setQq("10001");c.setAccessToken("test-secret");c.setApiTimeoutMs(2000);
        CountDownLatch completed=new CountDownLatch(1),received=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<JSONObject> action=new AtomicReference<>();
        OnebotWebSocketServer server=new OnebotWebSocketServer("integration",c,event->{
            if (!(event instanceof BbReceiveMessage message)) return;
            try {
                BbSendMessage reply=new BbSendMessage(message);reply.setMessageList(List.of(BbMessageContent.buildTextContent("ack")));
                new OneBotMessageApi().sendMessage(reply);assertEquals("-456",reply.getSentMessageId());
            } catch(Throwable e){failure.set(e);} finally{completed.countDown();}
        },0);
        WebSocketClient good=null,bad=null;
        try {
            for(int i=0;i<100&&server.getPort()==0;i++) Thread.sleep(10);
            assertTrue(server.getPort()>0);
            bad=gateway(server.getPort(),"wrong-token",new AtomicReference<>(),new CountDownLatch(1));
            assertFalse(bad.connectBlocking(2,TimeUnit.SECONDS));
            assertNull(c.getSession().connection());
            good=gateway(server.getPort(),"test-secret",action,received);
            assertTrue(good.connectBlocking(2,TimeUnit.SECONDS));
            good.send("{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":10001,\"user_id\":20002,\"group_id\":30003,\"message_id\":-123,\"message\":[{\"type\":\"text\",\"data\":{\"text\":\"hello\"}}]}");
            assertTrue(received.await(3,TimeUnit.SECONDS));assertTrue(completed.await(3,TimeUnit.SECONDS));
            assertNull(failure.get());assertEquals("send_group_msg",action.get().getString("action"));
        } finally {
            if(good!=null)good.close();if(bad!=null)bad.close();server.shutdown();
        }
    }
}
