package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StardewGuideAssistantServiceTest {

    private StardewGuideService guideService;
    private AiChatService aiChatService;
    private StardewGuideAssistantService assistantService;

    @BeforeEach
    void setUp() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        guideService = new StardewGuideService(repository);
        aiChatService = mock(AiChatService.class);
        assistantService = new StardewGuideAssistantService(guideService, aiChatService);
    }

    @Test
    void expandsKeywordsRetrievesEvidenceAndSynthesizesNaturalAnswer() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("[\"恐龙蛋怎么获得\", \"恐龙蛋黄酱怎么做\", \"失踪的收集包\"]");
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT)))
                .thenReturn("第一颗恐龙蛋先放进大鸡舍孵化器孵恐龙，之后再用蛋黄酱机做恐龙蛋黄酱补电影院。");

        String answer = assistantService.answer("星露谷 恐龙蛋黄酱怎么弄，电影院要用");

        assertThat(answer).contains("先放进大鸡舍孵化器", "蛋黄酱机", "电影院");

        ArgumentCaptor<List<ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.LIGHT));
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.CHAT));
        String synthesisPrompt = textOf(promptCaptor.getAllValues().get(1).get(1));
        assertThat(synthesisPrompt)
                .contains("用户问题", "检索到的资料")
                .contains("恐龙蛋黄酱获取方式", "恐龙蛋获取方式", "失踪的收集包");
        assertThat(synthesisPrompt).doesNotContain("sourceUrls", "gameVersion", "lastCheckedAt");
    }

    @Test
    void acceptsLineBasedKeywordOutput() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("1. 矮人卷轴\n2. 矮人语教程");
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT)))
                .thenReturn("矮人卷轴可以按层数刷，四卷都捐给博物馆后拿矮人语教程。");

        String answer = assistantService.answer("星露谷 矮人卷轴在哪刷");

        assertThat(answer).contains("矮人卷轴", "矮人语教程");
        ArgumentCaptor<List<ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.LIGHT));
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.CHAT));
        assertThat(textOf(promptCaptor.getAllValues().get(1).get(1))).contains("矮人卷轴获取方式");
    }

    @Test
    void fallsBackToSingleLookupWhenAiIsUnavailable() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenReturn(null);
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT))).thenReturn(null);

        String answer = assistantService.answer("星露谷 电池组怎么获得");

        assertThat(answer).contains("电池组获取方式", "避雷针");
    }

    @Test
    void fallsBackToSingleLookupWhenAiThrows() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        String answer = assistantService.answer("星露谷 斧头升级需要什么");

        assertThat(answer).contains("斧头", "铜斧", "铜锭 x5", "铁匠铺");
    }

    private String textOf(ChatMessage message) {
        return message.getContents().stream()
                .map(content -> content.getValue() == null ? "" : content.getValue())
                .reduce("", (a, b) -> a + b);
    }
}
