package com.bb.bot.common.util.aiChat.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelRouterTest {

    @Mock
    AiChatService aiChatService;

    @Mock
    AIProviderProperties properties;

    @InjectMocks
    ModelRouter router;

    @BeforeEach
    void setUp() {
        AIProviderProperties.Roles roles = new AIProviderProperties.Roles();
        roles.setLight("light");
        roles.setHeavy("heavy");
        when(properties.getRoles()).thenReturn(roles);
    }

    @Test
    void classify_rendersHistoryAndLatestUserMessageForClassifier() {
        when(aiChatService.chat(org.mockito.ArgumentMatchers.anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("SIMPLE");

        ModelTier tier = router.classify(List.of(
                ChatMessage.system("router prompt"),
                ChatMessage.user("alice：星露谷玛妮喜欢什么"),
                ChatMessage.assistant("玛妮最爱粉红蛋糕、农夫午餐、南瓜派和钻石。"),
                ChatMessage.user("alice：最爱礼物哪个最好做")
        ));

        assertThat(tier).isEqualTo(ModelTier.LIGHT);
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiChatService).chat(captor.capture(), eq(ModelTier.LIGHT));

        List<ChatMessage> request = captor.getValue();
        assertThat(request).hasSize(2);
        String classifierInput = request.get(1).getContents().get(0).getValue();
        assertThat(classifierInput)
                .contains("历史用户：alice：星露谷玛妮喜欢什么")
                .contains("历史助手：玛妮最爱粉红蛋糕、农夫午餐、南瓜派和钻石。")
                .contains("最新用户消息：alice：最爱礼物哪个最好做");
    }
}
