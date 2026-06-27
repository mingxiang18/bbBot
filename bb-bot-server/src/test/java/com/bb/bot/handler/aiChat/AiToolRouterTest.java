package com.bb.bot.handler.aiChat;

import com.bb.bot.aiAgent.core.AiToolDescriptor;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelRouter;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.common.util.aiChat.provider.VisionDescriber;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolRouterTest {

    @Mock
    ModelRouter modelRouter;

    @Mock
    AiToolRegistry toolRegistry;

    @Mock
    VisionDescriber visionDescriber;

    @InjectMocks
    AiToolRouter router;

    @Test
    void stardewFollowup_selectsStardewTool_withoutRepeatingKeyword() {
        when(toolRegistry.get("stardew_guide")).thenReturn(mock(AiToolDescriptor.class));

        ToolRouteDecision decision = router.decide(context(
                "最爱礼物哪个最好做",
                false,
                history("u1", "alice", "星露谷玛妮喜欢什么"),
                history("bot", "bot", "玛妮最爱粉红蛋糕、农夫午餐、南瓜派和钻石。")
        ), true);

        assertThat(decision.getMode()).isEqualTo(ToolRouteMode.SELECTED_TOOLS);
        assertThat(decision.getToolNames()).containsExactly("stardew_guide");
        assertThat(decision.getReason()).isEqualTo("STARDEW_CONTEXT_FOLLOWUP");
        verifyNoInteractions(modelRouter);
    }

    @Test
    void stardewHistory_switchTopic_doesNotInheritSelectedTool() {
        when(modelRouter.classify(anyList())).thenReturn(ModelTier.LIGHT);
        when(visionDescriber.enabled()).thenReturn(false);

        ToolRouteDecision decision = router.decide(context(
                "明天天气怎么样",
                true,
                history("u1", "alice", "星露谷玛妮喜欢什么"),
                history("bot", "bot", "玛妮最爱粉红蛋糕、农夫午餐、南瓜派和钻石。")
        ), true);

        assertThat(decision.getMode()).isEqualTo(ToolRouteMode.NONE);
        assertThat(decision.getTier()).isEqualTo(ModelTier.LIGHT);
    }

    @Test
    void directRouting_sendsShortTermContextToModelRouter() {
        when(modelRouter.classify(anyList())).thenReturn(ModelTier.CHAT);

        ToolRouteDecision decision = router.decide(context(
                "继续查一下具体来源",
                true,
                history("u1", "alice", "帮我查一下今天的新闻"),
                history("bot", "bot", "我先看了几条相关新闻。")
        ), true);

        assertThat(decision.getMode()).isEqualTo(ToolRouteMode.ALL_TOOLS);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(modelRouter).classify(captor.capture());
        List<ChatMessage> messages = captor.getValue();
        assertThat(messages).hasSizeGreaterThanOrEqualTo(4);
        assertThat(messages.get(0).getRole()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(messages.get(0).getContents().get(0).getValue()).contains("工具路由器");
        assertThat(messages.stream()
                .filter(m -> m.getRole() == ChatMessage.Role.USER)
                .map(m -> m.getContents().get(0).getValue()))
                .anyMatch(text -> text.contains("帮我查一下今天的新闻"))
                .anyMatch(text -> text.contains("继续查一下具体来源"));
    }

    @Test
    void imageRef_keepsImageOnlyRouteWhenNoGeneralTools() {
        when(visionDescriber.enabled()).thenReturn(true);

        ToolRouteDecision decision = router.decide(AiChatTurnContext.builder()
                .currentContent(List.of(BbMessageContent.buildNetImageMessageContent("https://example.test/a.png")))
                .historyList(List.of())
                .currentText("看看这个")
                .directTrigger(false)
                .autoReply(true)
                .build(), true);

        assertThat(decision.getMode()).isEqualTo(ToolRouteMode.IMAGE_ONLY);
        assertThat(decision.getToolNames()).containsExactly("analyze_image");
    }

    private static AiChatTurnContext context(String currentText, boolean direct, ChatHistory... history) {
        return AiChatTurnContext.builder()
                .currentContent(List.of(BbMessageContent.buildTextContent(currentText)))
                .historyList(List.of(history))
                .currentText(currentText)
                .directTrigger(direct)
                .autoReply(!direct)
                .build();
    }

    private static ChatHistory history(String userQq, String userName, String text) {
        ChatHistory history = new ChatHistory();
        history.setUserQq(userQq);
        history.setUserName(userName);
        history.setText("[{\"type\":\"text\",\"data\":\"" + text + "\"}]");
        return history;
    }
}
