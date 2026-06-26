package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StardewGuideRetrieverTest {

    private StardewGuideRetriever retriever;

    @BeforeEach
    void setUp() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        retriever = new StardewGuideRetriever(new StardewGuideService(repository));
    }

    @Test
    void retrievesMultipleTypedIntentsWithoutLettingOneIntentDominate() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.ANIMAL_CARE, "动物怎么养，怎么提高心情和好感"),
                intent(StardewGuideIntent.RESOURCE, "大壶牛奶为什么不出")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("动物怎么养，大壶牛奶为什么不出", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.ANIMAL_CARE, StardewGuideIntent.RESOURCE);
        assertThat(joinAnswers(evidence)).contains("每天摸动物", "好感", "奶牛", "挤奶桶");
    }

    @Test
    @SuppressWarnings("deprecation")
    void retrievesThroughExplicitEvidenceApiInsteadOfLegacyFreeTextRoute() {
        StardewGuideService guideService = mock(StardewGuideService.class);
        when(guideService.answerEvidence(StardewGuideIntent.RESOURCE, "电池组怎么获得"))
                .thenReturn(StardewGuideResult.builder()
                        .intent("resource")
                        .answer("电池组获取方式：避雷针。")
                        .build());
        StardewGuideRetriever retriever = new StardewGuideRetriever(guideService);
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.RESOURCE, "电池组怎么获得"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 电池组怎么获得", plan);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).answer()).contains("电池组获取方式");
        verify(guideService).answerEvidence(StardewGuideIntent.RESOURCE, "电池组怎么获得");
        verify(guideService, never()).answer(anyString());
    }

    @Test
    void fruitTreeIntentPrefersFruitTreeGuideOverFruitResource() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.FRUIT_TREE, "苹果树温室怎么种"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("苹果树温室怎么种", plan);

        assertThat(evidence).isNotEmpty();
        assertThat(evidence.get(0).intent()).isEqualTo("guide");
        assertThat(evidence.get(0).answer()).contains("果树", "28 天", "3x3", "温室");
    }

    @Test
    void keepsBundleAndResourceQueriesSeparate() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "苹果怎么获得"),
                intent(StardewGuideIntent.BUNDLE, "饲料收集包需要什么")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("苹果怎么获得，饲料收集包需要什么", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.RESOURCE, StardewGuideIntent.BUNDLE);
        assertThat(joinAnswers(evidence)).contains("苹果获取方式", "苹果树", "饲料收集包");
    }

    @Test
    void machineIntentRetrievesCraftingDeviceDetails() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.MACHINE, "优质洒水器材料"),
                intent(StardewGuideIntent.MACHINE, "楼梯骷髅洞穴跳层")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("优质洒水器怎么做，楼梯怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.MACHINE);
        assertThat(joinAnswers(evidence)).contains("优质洒水器", "铁锭 x1", "精炼石英 x1", "楼梯", "石头 x99");
    }

    @Test
    void typedMachineIntentBypassesResourceRouteWhenNamesOverlap() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.MACHINE, "晶球破开器怎么做"),
                intent(StardewGuideIntent.MACHINE, "太阳能板怎么做，能产电池吗")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("晶球破开器和太阳能板怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("machine_detail");
        assertThat(joinAnswers(evidence))
                .contains("晶球破开器", "克林特特别订单", "钻石 x1")
                .contains("太阳能板", "卡洛琳特别订单", "电池组");
    }

    @Test
    void typedMachineIntentRetrievesCraftableConsumablesWithoutFallingBackToGenericGuide() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.MACHINE, "雨水图腾怎么做"),
                intent(StardewGuideIntent.MACHINE, "铱环怎么做")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("雨水图腾和铱环怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("machine_detail");
        assertThat(joinAnswers(evidence))
                .contains("雨水图腾", "硬木 x1", "松露油 x1", "松焦油 x5")
                .contains("铱环", "战斗 9 级", "铱锭 x5", "虚空精华 x50");
    }

    @Test
    void typedMachineIntentRetrievesFishingTackleAndBaitCraftingEvidence() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.MACHINE, "魔法鱼饵怎么做"),
                intent(StardewGuideIntent.MACHINE, "陷阱浮标怎么做")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("魔法鱼饵和陷阱浮标怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("machine_detail");
        assertThat(joinAnswers(evidence))
                .contains("魔法鱼饵", "放射性矿石 x1", "虫肉 x3")
                .contains("陷阱浮标", "钓鱼 6 级", "树液 x10");
    }

    @Test
    void typedResourceIntentStillReturnsResourceEvidenceForCraftedItems() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.RESOURCE, "恐龙蛋黄酱怎么做"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("恐龙蛋黄酱怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("resource");
        assertThat(joinAnswers(evidence)).contains("恐龙蛋黄酱获取方式", "蛋黄酱机", "失踪的收集包");
    }

    @Test
    void typedResourceIntentRetrievesMonsterLootEvidence() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "太阳精华哪里刷"),
                intent(StardewGuideIntent.RESOURCE, "蝙蝠翅膀哪里刷"),
                intent(StardewGuideIntent.RESOURCE, "骨头碎片怎么刷")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("太阳精华、蝙蝠翅膀和骨头碎片哪里刷", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.RESOURCE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("resource");
        assertThat(joinAnswers(evidence))
                .contains("太阳精华", "科罗布斯", "80g")
                .contains("蝙蝠翅膀", "31-39", "81-119")
                .contains("骨头碎片", "71-79", "姜岛");
    }

    @Test
    void typedBuildingIntentRetrievesLateGameAndCommunityBuildingEvidence() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.BUILDING, "沙漠方尖塔需要什么"),
                intent(StardewGuideIntent.BUILDING, "潘姆房子社区升级需要什么")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("沙漠方尖塔和潘姆房子需要什么", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.BUILDING);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("building_detail");
        assertThat(joinAnswers(evidence))
                .contains("沙漠方尖塔", "铱锭 x20", "仙人掌果子 x10")
                .contains("潘姆房屋社区升级", "500,000g", "木材 x950");
    }

    @Test
    void typedSkillIntentRetrievesCombatLevelingEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SKILL, "战斗等级低怎么快速升级"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("我战斗等级低怎么快速升级", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SKILL);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("战斗技能", "击杀怪物", "战斗季刊", "40-79 层")
                .contains("怪物香水", "战士 -> 野蛮人");
    }

    @Test
    void typedSkillIntentRetrievesFishingLevelingEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SKILL, "钓鱼等级低怎么快速升级"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("我钓鱼等级低怎么快速升级", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SKILL);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("钓鱼技能", "训练用鱼竿", "气泡点", "完美钓鱼")
                .contains("海泡布丁", "渔夫 -> 垂钓者")
                .doesNotContain("夏季鱼类");
    }

    @Test
    void typedSkillIntentRetrievesMiningLevelingEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SKILL, "采矿等级低怎么快速升级"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("我采矿等级低怎么快速升级", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SKILL);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("采矿技能", "怪物破坏岩石不给经验", "40-79 层", "骷髅洞穴")
                .contains("矿工特供", "矿工 -> 勘探者")
                .doesNotContain("鸡舍", "夏季鱼类");
    }

    @Test
    void typedSkillIntentRetrievesFarmingLevelingEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SKILL, "耕种等级低怎么快速升级"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("我耕种等级低怎么快速升级", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SKILL);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("耕种技能", "13 个防风草", "洒水器", "农夫午餐")
                .contains("农耕人 -> 工匠")
                .doesNotContain("夏季鱼类", "春季作物：");
    }

    @Test
    void typedSkillIntentRetrievesForagingLevelingEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SKILL, "觅食等级低怎么快速升级"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("我觅食等级低怎么快速升级", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SKILL);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("觅食技能", "秘密森林", "冬季野种子", "炸弹炸倒树不给觅食经验")
                .contains("收集者 -> 植物学家")
                .doesNotContain("夏季鱼类", "春季作物：", "硬木怎么获取：");
    }

    @Test
    void typedGuideIntentRetrievesBookEffectEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.GUIDE, "价格目录有什么用，星之书有什么用"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 价格目录和星之书有什么用", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("技能书与书商", "《价格目录》3,000g", "《星之书》给所有技能各 250 经验")
                .contains("力量书第一次读给永久能力")
                .doesNotContain("夏季鱼类", "鸡舍", "工具升级");
    }

    @Test
    void typedShopIntentRetrievesBookSellerItemEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SHOP, "酱料女皇食谱在哪里买"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 酱料女皇食谱在哪里买", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SHOP);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("shop_item");
        assertThat(joinAnswers(evidence))
                .contains("书商", "酱料女皇食谱", "50,000g", "100 个金核桃")
                .doesNotContain("夏季鱼类", "工具升级");
    }

    @Test
    void typedShopIntentRetrievesCommonMerchantEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.SHOP, "熔岩武士刀在哪里买"),
                intent(StardewGuideIntent.SHOP, "香蕉树苗怎么换"),
                intent(StardewGuideIntent.SHOP, "马笛在哪里买"),
                intent(StardewGuideIntent.SHOP, "咖啡在哪里买")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 熔岩武士刀、香蕉树苗、马笛和咖啡在哪里弄", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SHOP);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("shop_item");
        assertThat(joinAnswers(evidence))
                .contains("冒险家公会", "熔岩武士刀", "25,000g")
                .contains("姜岛商人", "香蕉树苗", "龙牙 x5")
                .contains("齐先生核桃房", "马笛", "50 齐钻")
                .contains("星之果实餐吧", "咖啡", "300g")
                .doesNotContain("夏季鱼类", "工具升级", "建筑材料", "果树怎么种");
    }

    @Test
    void typedGuideIntentRetrievesFoodBuffRulesEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.GUIDE, "料理buff和饮料buff怎么叠加"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 料理buff和饮料buff怎么叠加", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("技能食物增益", "只能有 1 组食物 buff 和 1 组饮料 buff")
                .contains("姜汁汽水是饮料类运气 +1", "不要连续吃不同 buff 食物")
                .doesNotContain("夏季鱼类", "鸡舍", "工具升级");
    }

    @Test
    void typedCookingIntentStillRetrievesSkullCavernFoodList() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.COOKING, "骷髅洞穴吃什么料理 buff 好"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 骷髅洞穴吃什么料理 buff 好", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.COOKING);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("cooking_available");
        assertThat(joinAnswers(evidence))
                .contains("香辣鳗鱼", "幸运午餐", "三倍浓缩咖啡", "魔法糖冰棍")
                .contains("约 7 分钟", "约 4 分钟")
                .doesNotContain("夏季鱼类", "鸡舍", "工具升级");
    }

    @Test
    void typedCookingIntentRetrievesCommonRecipeDetails() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.COOKING, "巧克力蛋糕怎么做"),
                intent(StardewGuideIntent.COOKING, "鱼肉卷材料和效果"),
                intent(StardewGuideIntent.COOKING, "粉红蛋糕怎么做"),
                intent(StardewGuideIntent.COOKING, "红之盛宴材料和效果"),
                intent(StardewGuideIntent.COOKING, "香蕉布丁怎么做"),
                intent(StardewGuideIntent.COOKING, "墨汁意大利饺效果")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 巧克力蛋糕、鱼肉卷、粉红蛋糕、红之盛宴、香蕉布丁和墨汁意大利饺怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.COOKING);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("cooking_recipe");
        assertThat(joinAnswers(evidence))
                .contains("巧克力蛋糕", "小麦粉 x1", "糖 x1", "鸡蛋 x1")
                .contains("鱼肉卷", "金枪鱼 x1", "玉米饼 x1", "钓鱼 +2")
                .contains("粉红蛋糕", "甜瓜 x1")
                .contains("红之盛宴", "红叶卷心菜 x1", "最大体力 +50")
                .contains("香蕉布丁", "香蕉 x1", "骨头碎片 x30")
                .contains("墨汁意大利饺", "鱿鱼墨汁 x1", "免疫负面效果")
                .doesNotContain("夏季鱼类", "鸡舍", "工具升级", "怎么获得");
    }

    @Test
    void typedIntentMissDoesNotFallBackToFreeTextRouting() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.RESOURCE, "鸡舍升级材料"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 鸡舍升级材料", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.RESOURCE);
        assertThat(joinAnswers(evidence))
                .contains("没找到这个资源")
                .doesNotContain("鸡舍", "建造费用", "罗宾");
    }

    @Test
    void unknownIntentDoesNotUseLegacyFreeTextRouting() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.UNKNOWN, "鸡舍升级材料"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 鸡舍升级材料", plan);

        assertThat(evidence).isEmpty();
    }

    private StardewQueryPlan plan(StardewQueryPlan.PlannedIntent... intents) {
        StardewQueryPlan plan = new StardewQueryPlan();
        plan.setIntents(List.of(intents));
        return plan;
    }

    private StardewQueryPlan.PlannedIntent intent(StardewGuideIntent type, String... keywords) {
        StardewQueryPlan.PlannedIntent intent = new StardewQueryPlan.PlannedIntent();
        intent.setType(type);
        intent.setKeywords(List.of(keywords));
        return intent;
    }

    private String joinAnswers(List<StardewGuideEvidence> evidence) {
        return evidence.stream()
                .map(StardewGuideEvidence::answer)
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
