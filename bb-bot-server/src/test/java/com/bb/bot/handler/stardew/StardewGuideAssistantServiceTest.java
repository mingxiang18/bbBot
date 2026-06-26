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
import static org.mockito.Mockito.never;
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
        StardewQueryPlannerService plannerService = new StardewQueryPlannerService(aiChatService);
        StardewGuideRetriever retriever = new StardewGuideRetriever(guideService);
        assistantService = new StardewGuideAssistantService(guideService, plannerService, retriever, aiChatService);
    }

    @Test
    void plansTypedQueriesRetrievesEvidenceAndSynthesizesNaturalAnswer() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {"type":"RESOURCE","keywords":["恐龙蛋怎么获得"],"constraints":{}},
                            {"type":"RESOURCE","keywords":["恐龙蛋黄酱怎么做"],"constraints":{}},
                            {"type":"BUNDLE","keywords":["失踪的收集包"],"constraints":{}}
                          ]
                        }
                        """);
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT)))
                .thenReturn("第一颗恐龙蛋先放进大鸡舍孵化器孵恐龙，之后再用蛋黄酱机做恐龙蛋黄酱补电影院。");

        String answer = assistantService.answer("星露谷 恐龙蛋黄酱怎么弄，电影院要用");

        assertThat(answer).contains("先放进大鸡舍孵化器", "蛋黄酱机", "电影院");

        ArgumentCaptor<List<ChatMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.LIGHT));
        verify(aiChatService).chat(promptCaptor.capture(), eq(ModelTier.CHAT));
        String plannerPrompt = textOf(promptCaptor.getAllValues().get(0).get(0));
        String synthesisPrompt = textOf(promptCaptor.getAllValues().get(1).get(1));
        assertThat(plannerPrompt).contains("type 只能从这些枚举中选择", "RESOURCE", "BUNDLE", "MONSTER_DROP");
        assertThat(synthesisPrompt)
                .contains("用户问题", "检索到的资料")
                .contains("类型：RESOURCE", "查询：恐龙蛋怎么获得", "恐龙蛋获取方式")
                .contains("类型：BUNDLE", "查询：失踪的收集包", "失踪的收集包");
        assertThat(synthesisPrompt).doesNotContain("sourceUrls", "gameVersion", "lastCheckedAt");
    }

    @Test
    void returnsClarificationWhenSchedulePlanNeedsGameTime() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": true,
                          "clarificationQuestion": "要判断海莉现在在哪，请补充游戏内时间；有季节、日期、星期、天气会更准。",
                          "intents": [
                            {"type":"VILLAGER_SCHEDULE","keywords":["海莉在哪"],"constraints":{"villager":"海莉"}}
                          ]
                        }
                        """);

        String answer = assistantService.answer("海莉现在在哪");

        assertThat(answer).contains("请补充游戏内时间", "季节", "天气");
        verify(aiChatService).chat(anyList(), eq(ModelTier.LIGHT));
        verify(aiChatService, never()).chat(anyList(), eq(ModelTier.CHAT));
    }

    @Test
    void fallsBackToTypedLookupWhenPlanIsUnavailable() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenReturn(null);
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT))).thenReturn(null);

        String answer = assistantService.answer("星露谷 电池组怎么获得");

        assertThat(answer).contains("电池组获取方式", "避雷针");
    }

    @Test
    void fallsBackToTypedLookupWhenAiThrows() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        String answer = assistantService.answer("星露谷 斧头升级需要什么");

        assertThat(answer).contains("斧头", "铜斧", "铜锭 x5", "铁匠铺");
    }

    @Test
    void typedPlanMissDoesNotFallBackToFreeTextRouteWhenSynthesisFails() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {"type":"RESOURCE","keywords":["鸡舍升级材料"],"constraints":{}}
                          ]
                        }
                        """);
        when(aiChatService.chat(anyList(), eq(ModelTier.CHAT))).thenReturn(null);

        String answer = assistantService.answer("星露谷 鸡舍升级材料");

        assertThat(answer)
                .contains("没找到这个资源")
                .doesNotContain("罗宾", "建造费用", "升级费用");
    }

    @Test
    void unknownPlanDoesNotUseLegacyFreeTextRoute() {
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {"type":"UNKNOWN","keywords":["鸡舍升级材料"],"constraints":{}}
                          ]
                        }
                        """);

        String answer = assistantService.answer("星露谷 鸡舍升级材料");

        assertThat(answer)
                .contains("还没有查到足够确定的攻略内容")
                .doesNotContain("罗宾", "建造费用", "木材");
        verify(aiChatService).chat(anyList(), eq(ModelTier.LIGHT));
        verify(aiChatService, never()).chat(anyList(), eq(ModelTier.CHAT));
    }

    private String textOf(ChatMessage message) {
        return message.getContents().stream()
                .map(content -> content.getValue() == null ? "" : content.getValue())
                .reduce("", (a, b) -> a + b);
    }
}
