package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StardewQueryPlannerServiceTest {

    @Test
    void parsesTypedPlanAndConstraintsFromAiJson() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        ```json
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "FISH",
                              "keywords": ["夏天能钓什么鱼"],
                              "constraints": {"season": "夏季", "location": "河流", "weather": "雨天", "time": "18:00"}
                            },
                            {
                              "type": "RESOURCE",
                              "keywords": ["电池组怎么获得"],
                              "constraints": {}
                            }
                          ]
                        }
                        ```
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("夏季雨天 18:00 河流能钓什么鱼，电池组怎么获得");

        assertThat(plan.isNeedMoreInfo()).isFalse();
        assertThat(plan.getIntents()).hasSize(2);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FISH);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("夏天能钓什么鱼");
        assertThat(plan.getIntents().get(0).getConstraints().getSeason()).isEqualTo("夏季");
        assertThat(plan.getIntents().get(0).getConstraints().getLocation()).isEqualTo("河流");
        assertThat(plan.getIntents().get(1).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
    }

    @Test
    void fallsBackToTypedIntentWhenAiJsonIsInvalid() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenReturn("我觉得可以去矿井看看");

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("矮人卷轴在哪刷");

        assertThat(plan.isNeedMoreInfo()).isFalse();
        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.MUSEUM);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("矮人卷轴在哪刷");
    }

    @Test
    void localFallbackClassifiesToolUpgradeQueries() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("斧头升级需要什么");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.TOOL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("斧头升级需要什么");
    }

    @Test
    void localFallbackClassifiesFishingLevelingQueriesAsSkill() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("钓鱼等级低怎么快速升级");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SKILL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("钓鱼等级低怎么快速升级");
    }

    @Test
    void localFallbackClassifiesMiningLevelingQueriesAsSkill() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("采矿等级低怎么快速升级");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SKILL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("采矿等级低怎么快速升级");
    }

    @Test
    void localFallbackClassifiesFarmingLevelingQueriesAsSkill() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("耕种等级低怎么快速升级");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SKILL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("耕种等级低怎么快速升级");
    }

    @Test
    void localFallbackClassifiesForagingLevelingQueriesAsSkill() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("觅食等级低怎么快速升级");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SKILL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("觅食等级低怎么快速升级");
    }

    @Test
    void localFallbackClassifiesBookEffectQueriesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("价格目录有什么用，值得买吗");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("价格目录有什么用，值得买吗");
    }

    @Test
    void localFallbackClassifiesBookPurchaseQueriesAsShop() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("酱料女皇食谱在哪里买");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("酱料女皇食谱在哪里买");
    }

    @Test
    void localFallbackClassifiesBuffStackingRulesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("料理buff和饮料buff怎么叠加，会互相覆盖吗");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("料理buff和饮料buff怎么叠加，会互相覆盖吗");
    }

    @Test
    void localFallbackStillClassifiesSkullCavernFoodQuestionsAsCooking() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("骷髅洞穴吃什么料理buff好");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.COOKING);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("骷髅洞穴吃什么料理buff好");
    }

    @Test
    void localFallbackClassifiesDishNameMaterialQuestionsAsCooking() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("巧克力蛋糕怎么做，鳟鱼汤材料是什么");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.COOKING);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("巧克力蛋糕怎么做，鳟鱼汤材料是什么");
    }

    @Test
    void parsesSkillIntentForCombatLevelingQuestions() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "SKILL",
                              "keywords": ["战斗等级低怎么快速升级", "战斗职业怎么选"],
                              "constraints": {}
                            }
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("我战斗等级低怎么快速升级，职业怎么选");

        assertThat(plan.isNeedMoreInfo()).isFalse();
        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SKILL);
        assertThat(plan.getIntents().get(0).getKeywords())
                .containsExactly("战斗等级低怎么快速升级", "战斗职业怎么选");
    }

    @Test
    void normalizesMissingIntentFields() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "intents": [
                            {"type": null, "keywords": null, "constraints": null}
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("斧头升级需要什么");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.TOOL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("斧头升级需要什么");
        assertThat(plan.getIntents().get(0).getConstraints()).isNotNull();
    }
}
