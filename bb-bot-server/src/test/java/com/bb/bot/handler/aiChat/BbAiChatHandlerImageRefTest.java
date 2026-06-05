package com.bb.bot.handler.aiChat;

import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 覆盖 {@link BbAiChatHandler#hasImageRef}（决定非工具回复是否挂 analyze_image）。 */
class BbAiChatHandlerImageRefTest {

    private static ChatHistory hist(String text) {
        ChatHistory h = new ChatHistory();
        h.setText(text);
        return h;
    }

    @Test
    void currentNetImage_true() {
        List<BbMessageContent> cur = List.of(
                BbMessageContent.builder().type(BbSendMessageType.NET_IMAGE).data("/img/h.png").build());
        assertTrue(BbAiChatHandler.hasImageRef(cur, null));
    }

    @Test
    void historyWithRefMarker_true() {
        List<BbMessageContent> cur = List.of(BbMessageContent.buildTextContent("hi"));
        assertTrue(BbAiChatHandler.hasImageRef(cur, List.of(hist("[图片 ref=abc 链接:/img/abc.png]"))));
    }

    @Test
    void historyWithNetImageJson_true() {
        assertTrue(BbAiChatHandler.hasImageRef(null,
                List.of(hist("[{\"type\":\"netImage\",\"data\":\"/img/x.png\"}]"))));
    }

    @Test
    void noImage_false() {
        assertFalse(BbAiChatHandler.hasImageRef(
                List.of(BbMessageContent.buildTextContent("hi")), List.of(hist("just text"))));
    }

    @Test
    void nulls_false() {
        assertFalse(BbAiChatHandler.hasImageRef(null, null));
    }

    @Test
    void imageOnlyBeyondRecentWindow_false() {
        // hasImageRef 只看最近 8 条历史：把含图项放在更早处，应不命中
        List<ChatHistory> history = new ArrayList<>();
        history.add(hist("netImage /img/old.png"));   // index 0（最早）
        for (int i = 0; i < 8; i++) {
            history.add(hist("plain " + i));
        }
        assertFalse(BbAiChatHandler.hasImageRef(null, history));
    }
}
