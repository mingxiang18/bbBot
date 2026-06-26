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
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
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
    void aiClassificationRemainsPrimaryWhenItChoosesConcreteNonGuideIntent() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "RESOURCE",
                              "keywords": ["小桶怎么做"],
                              "constraints": {}
                            }
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("小桶怎么做");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
    }

    @Test
    void localGuardrailOnlyPullsBroadGuideBackToConcreteCraftingIntent() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "GUIDE",
                              "keywords": ["木栅栏怎么做"],
                              "constraints": {}
                            }
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("木栅栏怎么做");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.CRAFTING);
    }

    @Test
    void localFallbackClassifiesMonsterDropQueriesSeparatelyFromResourceQueries() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);
        StardewQueryPlan dustSprite = planner.plan("煤尘精灵掉什么");
        StardewQueryPlan serpent = planner.plan("飞蛇在哪刷");
        StardewQueryPlan voidEssence = planner.plan("虚空精华哪里刷");
        StardewQueryPlan monsterCompendium = planner.plan("怪物图鉴有什么用");

        assertThat(dustSprite.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.MONSTER_DROP);
        assertThat(serpent.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.MONSTER_DROP);
        assertThat(voidEssence.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(monsterCompendium.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
    }

    @Test
    void localFallbackClassifiesFishPondProductQueriesSeparatelyFromBuildingAndFishing() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);
        StardewQueryPlan sturgeonProducts = planner.plan("鲟鱼鱼塘产什么");
        StardewQueryPlan pondRecommendations = planner.plan("鱼塘养什么好");
        StardewQueryPlan pondBuilding = planner.plan("鱼塘建造材料多少钱");
        StardewQueryPlan summerFishing = planner.plan("夏天能钓什么鱼");

        assertThat(sturgeonProducts.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FISH_POND);
        assertThat(pondRecommendations.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FISH_POND);
        assertThat(pondBuilding.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.BUILDING);
        assertThat(summerFishing.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FISH);
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
    void localFallbackClassifiesDetailedBookEffectQueriesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("战斗季刊有什么用");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("战斗季刊有什么用");
    }

    @Test
    void localFallbackClassifiesDetailedBookPurchaseQueriesAsShop() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("矮人安全手册在哪里买");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("矮人安全手册在哪里买");
    }

    @Test
    void localFallbackKeepsMerchantHourQueriesOnShopRoute() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("冒险家公会几点开");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("冒险家公会几点开");
    }

    @Test
    void localFallbackClassifiesExchangeMerchantQueriesAsShop() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("齐钻商店卖什么");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("齐钻商店卖什么");
    }

    @Test
    void localFallbackClassifiesSpecialMerchantUnlockQueriesAsShop() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("赌场怎么进");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("赌场怎么进");
    }

    @Test
    void localFallbackClassifiesRaccoonShopUnlockQueriesAsShop() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("浣熊商店怎么解锁");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("浣熊商店怎么解锁");
    }

    @Test
    void localFallbackClassifiesFestivalEventQueriesAsFestival() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("沙漠节换什么，花舞节几点开始");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FESTIVAL);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("沙漠节换什么，花舞节几点开始");
    }

    @Test
    void localFallbackKeepsSpecificFestivalShopItemsOnShopRoute() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("草莓种子在哪里买");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.SHOP);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("草莓种子在哪里买");
    }

    @Test
    void localGuardrailPullsBroadGuideBackToConcreteFestivalIntent() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "GUIDE",
                              "keywords": ["星露谷展览会怎么拿星之果实"],
                              "constraints": {}
                            }
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("星露谷展览会怎么拿星之果实");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FESTIVAL);
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
    void localFallbackClassifiesTrinketAndAnvilQueriesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        StardewQueryPlan trinkets = planner.plan("小饰品哪个好，怎么获得");
        StardewQueryPlan frogEgg = planner.plan("青蛙蛋适合刷怪物掉落吗");
        StardewQueryPlan magicQuiver = planner.plan("魔法箭筒铁砧刷什么词条");

        assertThat(trinkets.getIntents()).hasSize(1);
        assertThat(trinkets.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(frogEgg.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(magicQuiver.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
    }

    @Test
    void localFallbackClassifiesSpecificMineralQueriesAsResourcesAndBroadMuseumQueriesAsMuseum() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        StardewQueryPlan topaz = planner.plan("黄水晶这个矿物哪里找");
        StardewQueryPlan marble = planner.plan("大理石开哪个晶球");
        StardewQueryPlan starShards = planner.plan("陶瓷碎片怎么获得");
        StardewQueryPlan museum = planner.plan("博物馆缺矿物怎么补");

        assertThat(topaz.getIntents()).hasSize(1);
        assertThat(topaz.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(marble.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(starShards.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(museum.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.MUSEUM);
    }

    @Test
    void localFallbackClassifiesSpecificArtifactQueriesAsResourcesAndBroadMuseumQueriesAsMuseum() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        StardewQueryPlan ancientDoll = planner.plan("古代玩偶这个古物怎么获得");
        StardewQueryPlan dwarfScrollTwo = planner.plan("矮人卷轴 II 哪里刷");
        StardewQueryPlan strangeDollYellow = planner.plan("黄色诡异玩偶怎么拿");
        StardewQueryPlan broadMuseum = planner.plan("博物馆缺古物怎么补");

        assertThat(ancientDoll.getIntents()).hasSize(1);
        assertThat(ancientDoll.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(dwarfScrollTwo.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(strangeDollYellow.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(broadMuseum.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.MUSEUM);
    }

    @Test
    void localFallbackClassifiesIslandFieldOfficeQueriesBeforeMuseumRoute() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        assertThat(planner.plan("岛屿办事处化石怎么捐").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(planner.plan("蜗牛教授化石奖励是什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(planner.plan("紫花和紫海星答案是多少").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(planner.plan("蛇头骨怎么获得").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("蛇椎骨哪里刷").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("木乃伊蝙蝠哪里刷").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("金色椰子怎么开").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("生姜怎么获得").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("岩浆菇哪里找").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("博物馆缺古物怎么补").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.MUSEUM);
    }

    @Test
    void localFallbackClassifiesSpecialOrdersWithoutStealingFishPondOrLegendaryFishQueries() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        assertThat(planner.plan("特别订单有哪些").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
        assertThat(planner.plan("罗宾资源冲刺奖励是什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
        assertThat(planner.plan("岛屿食材要什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
        assertThat(planner.plan("齐瓜怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
        assertThat(planner.plan("五彩农场交什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
        assertThat(planner.plan("岩浆鳗鱼鱼塘要什么任务物品").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.FISH_POND);
        assertThat(planner.plan("大家族任务传说鱼有哪些").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.FISH);
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
    void localFallbackClassifiesExpandedDishNamesAsCooking() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("南瓜派怎么做，蔓越莓酱效果是什么，墨汁意大利饺和芒果糯米饭材料是什么");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.COOKING);
        assertThat(plan.getIntents().get(0).getKeywords())
                .containsExactly("南瓜派怎么做，蔓越莓酱效果是什么，墨汁意大利饺和芒果糯米饭材料是什么");
    }

    @Test
    void localFallbackClassifiesCraftingRecipeQueriesSeparatelyFromResourceCookingAndSpecialOrders() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        assertThat(planner.plan("木栅栏怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.CRAFTING);
        assertThat(planner.plan("茶苗材料是什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.CRAFTING);
        assertThat(planner.plan("树液采集器配方").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.CRAFTING);
        assertThat(planner.plan("迷你锻造台怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.CRAFTING);
        assertThat(planner.plan("巧克力蛋糕怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.COOKING);
        assertThat(planner.plan("恐龙蛋黄酱怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("齐瓜怎么做").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.SPECIAL_ORDER);
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
    void localFallbackClassifiesSpecialCurrencyQueries() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlannerService planner = new StardewQueryPlannerService(aiChatService);

        assertThat(planner.plan("特殊货币有哪些").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(planner.plan("齐钻怎么获得").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("金核桃怎么用").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("三花蛋换什么").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("金色标签怎么获得").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("星星币怎么刷").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
        assertThat(planner.plan("火山晶石怎么用").getIntents().get(0).getType())
                .isEqualTo(StardewGuideIntent.RESOURCE);
    }

    @Test
    void localFallbackClassifiesForgeEnchantingQueriesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("银河剑怎么锻造，工具附魔哪个好");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(plan.getIntents().get(0).getKeywords()).containsExactly("银河剑怎么锻造，工具附魔哪个好");
    }

    @Test
    void localFallbackClassifiesMasteryRewardQueriesAsGuide() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT))).thenThrow(new RuntimeException("ai down"));

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("精通先选哪个，高级铱金鱼竿怎么获得，挑战鱼饵怎么做");

        assertThat(plan.getIntents()).hasSize(1);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.GUIDE);
        assertThat(plan.getIntents().get(0).getKeywords())
                .containsExactly("精通先选哪个，高级铱金鱼竿怎么获得，挑战鱼饵怎么做");
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

    @Test
    void normalizesUnsafeAiIntentTypesWithLocalHighConfidenceBoundaries() {
        AiChatService aiChatService = mock(AiChatService.class);
        when(aiChatService.chat(anyList(), eq(ModelTier.LIGHT)))
                .thenReturn("""
                        {
                          "needMoreInfo": false,
                          "intents": [
                            {
                              "type": "SPECIAL_ORDER",
                              "keywords": ["大家族任务传说鱼有哪些"],
                              "constraints": {}
                            },
                            {
                              "type": "SPECIAL_ORDER",
                              "keywords": ["岩浆鳗鱼鱼塘要什么任务物品"],
                              "constraints": {}
                            },
                            {
                              "type": "GUIDE",
                              "keywords": ["战斗等级低怎么快速升级"],
                              "constraints": {}
                            }
                          ]
                        }
                        """);

        StardewQueryPlan plan = new StardewQueryPlannerService(aiChatService)
                .plan("大家族任务传说鱼有哪些，岩浆鳗鱼鱼塘要什么任务物品，战斗等级低怎么快速升级");

        assertThat(plan.getIntents()).hasSize(3);
        assertThat(plan.getIntents().get(0).getType()).isEqualTo(StardewGuideIntent.FISH);
        assertThat(plan.getIntents().get(1).getType()).isEqualTo(StardewGuideIntent.FISH_POND);
        assertThat(plan.getIntents().get(2).getType()).isEqualTo(StardewGuideIntent.SKILL);
    }
}
