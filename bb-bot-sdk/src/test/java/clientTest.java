import com.alibaba.fastjson2.JSON;
import com.bb.bot.client.BbClientMessageHandler;
import com.bb.bot.client.BbWebSocketClient;
import com.bb.bot.entity.bb.BbSocketClientMessage;
import com.bb.bot.entity.bb.BbSocketServerMessage;
import org.java_websocket.WebSocket;

import java.net.URI;
import java.time.LocalDateTime;

public class clientTest {


    public static void main(String[] args) throws InterruptedException {
        BbWebSocketClient bbWebSocketClient = new BbWebSocketClient("test",
                "1234",
                "1234", 30000,
                URI.create("ws://localhost:30201"), new BbClientMessageHandler() {
            @Override
            public void handleMessage(WebSocket webSocket, BbSocketServerMessage message) {
                System.out.println(JSON.toJSONString(message));
            }
        });

        BbSocketClientMessage bbSocketClientMessage = new BbSocketClientMessage();
        bbSocketClientMessage.setMessageType("private");
        bbSocketClientMessage.setUserId("test");
        bbSocketClientMessage.setMessageId("124123123123");
        bbSocketClientMessage.setMessage("你好");
        bbSocketClientMessage.setSendTime(LocalDateTime.now());
        bbWebSocketClient.send(JSON.toJSONString(bbSocketClientMessage));

        Thread.sleep(10000);
    }
}
