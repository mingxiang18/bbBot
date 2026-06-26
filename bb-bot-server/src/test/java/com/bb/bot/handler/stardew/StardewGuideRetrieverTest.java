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
    void typedBundleIntentRetrievesRemixedBundlesWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.BUNDLE, "重混春季作物收集包需要什么"),
                intent(StardewGuideIntent.BUNDLE, "重混鱼农收集包需要什么"),
                intent(StardewGuideIntent.BUNDLE, "重混冒险家收集包交什么"),
                intent(StardewGuideIntent.BUNDLE, "重混冬日星盛宴收集包需要什么")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("重混春季作物、鱼农、冒险家、冬日星盛宴收集包怎么交", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.BUNDLE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("bundle");
        assertThat(joinAnswers(evidence))
                .contains("春季作物收集包（重混）", "胡萝卜", "6 选 4")
                .contains("鱼农收集包（重混）", "鱼籽", "鱿鱼墨汁")
                .contains("冒险家收集包（重混）", "骨头碎片", "5 选 4")
                .contains("冬日星盛宴收集包（重混）", "粉瓜", "神秘盒")
                .doesNotContain("夏季鱼类", "资源获取方式", "春季作物：", "工具升级");
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
    void typedMonsterDropIntentRetrievesMonsterTableWithoutResourceCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.MONSTER_DROP, "煤尘精灵掉什么"),
                intent(StardewGuideIntent.MONSTER_DROP, "飞蛇在哪刷"),
                intent(StardewGuideIntent.RESOURCE, "虚空精华哪里刷")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("煤尘精灵和飞蛇掉什么，虚空精华哪里刷", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.MONSTER_DROP, StardewGuideIntent.RESOURCE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .contains("monster_drop", "resource");
        assertThat(joinAnswers(evidence))
                .contains("煤尘精灵掉落表", "煤炭 (50%)")
                .contains("飞蛇掉落表", "骷髅洞穴", "兔子的脚")
                .contains("虚空精华获取方式", "科罗布斯")
                .doesNotContain("怪物图鉴：");
    }

    @Test
    void typedResourceIntentRetrievesSpecificMineralsWithoutMuseumGuideCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "黄水晶哪里找"),
                intent(StardewGuideIntent.RESOURCE, "大理石怎么获得"),
                intent(StardewGuideIntent.RESOURCE, "陶瓷碎片开哪个晶球"),
                intent(StardewGuideIntent.RESOURCE, "黑曜石哪里刷")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("黄水晶、大理石、陶瓷碎片、黑曜石哪里找", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.RESOURCE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("resource");
        assertThat(joinAnswers(evidence))
                .contains("黄水晶获取方式", "黄水晶矿点")
                .contains("大理石获取方式", "冰封晶球", "大理石火炬")
                .contains("陶瓷碎片获取方式", "岩浆晶球", "星星 T 恤")
                .contains("黑曜石获取方式", "塞巴斯蒂安最爱")
                .doesNotContain("博物馆捐赠：", "95 件", "42 件古物", "53 件矿物");
    }

    @Test
    void typedResourceIntentRetrievesSpecificArtifactsWithoutMuseumGuideCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "古代玩偶怎么获得"),
                intent(StardewGuideIntent.RESOURCE, "矮人卷轴 II 哪里刷"),
                intent(StardewGuideIntent.RESOURCE, "黄色诡异玩偶怎么拿"),
                intent(StardewGuideIntent.RESOURCE, "鹦鹉螺化石哪里找")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("古代玩偶、矮人卷轴 II、黄色诡异玩偶、鹦鹉螺化石怎么拿", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.RESOURCE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("resource");
        assertThat(joinAnswers(evidence))
                .contains("古代玩偶获取方式", "冬日星盛宴", "古物宝藏")
                .contains("矮人卷轴 II获取方式", "矿井 1-39", "煤尘精灵")
                .contains("诡异玩偶（黄）获取方式", "秘密纸条 #18")
                .contains("鹦鹉螺化石获取方式", "不是冬季沙滩采集物")
                .doesNotContain("95 件", "42 件古物", "53 件矿物");
    }

    @Test
    void typedIslandFieldOfficeIntentsRetrieveResourcesAndGuideWithoutMuseumCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "蛇头骨怎么获得"),
                intent(StardewGuideIntent.RESOURCE, "木乃伊蝙蝠哪里刷"),
                intent(StardewGuideIntent.RESOURCE, "金色椰子怎么开"),
                intent(StardewGuideIntent.GUIDE, "岛屿办事处化石奖励和紫花答案")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("蛇头骨、木乃伊蝙蝠、金色椰子和岛屿办事处怎么做", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.RESOURCE, StardewGuideIntent.GUIDE);
        assertThat(joinAnswers(evidence))
                .contains("蛇头骨获取方式", "姜岛西部", "木乃伊蝙蝠获取方式", "火山地牢")
                .contains("金色椰子获取方式", "克林特", "岛屿办事处化石", "蜗牛教授", "22", "18")
                .doesNotContain("博物馆捐赠：", "95 件", "42 件古物", "53 件矿物")
                .doesNotContain("星星币获取方式", "工具升级总览");
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
                .containsOnly("book_detail");
        assertThat(joinAnswers(evidence))
                .contains("书籍对照", "价格目录", "查看物品的出售价值")
                .contains("星之书", "所有技能各获得 250 经验", "1,125 精通点")
                .doesNotContain("夏季鱼类", "鸡舍", "工具升级");
    }

    @Test
    void typedGuideIntentRetrievesExactBookEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.GUIDE, "怪物图鉴有什么用"),
                intent(StardewGuideIntent.GUIDE, "战斗季刊怎么获得")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("怪物图鉴和战斗季刊有什么用", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("book_detail");
        assertThat(joinAnswers(evidence))
                .contains("怪物图鉴", "双倍战利品", "战斗季刊", "250 战斗经验")
                .doesNotContain("夏季鱼类", "工具升级", "鸡舍", "料理");
    }

    @Test
    void typedShopIntentRetrievesBookAcquisitionEvidence() {
        StardewQueryPlan plan = plan(intent(StardewGuideIntent.SHOP, "怪物图鉴在哪里买"));

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 怪物图鉴在哪里买", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SHOP);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("book_detail");
        assertThat(joinAnswers(evidence))
                .contains("怪物图鉴", "书商", "第 3 年", "20,000g", "双倍战利品")
                .doesNotContain("夏季鱼类", "工具升级", "居民位置");
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
    void typedShopIntentRetrievesSpecialMerchantEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.SHOP, "自动抚摸机在哪里买"),
                intent(StardewGuideIntent.SHOP, "赌场怎么进"),
                intent(StardewGuideIntent.SHOP, "幻觉神龛多少钱"),
                intent(StardewGuideIntent.SHOP, "胡萝卜种子在哪里买")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("星露谷 自动抚摸机、赌场、幻觉神龛和胡萝卜种子怎么弄", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SHOP);
        assertThat(joinAnswers(evidence))
                .contains("Joja", "自动抚摸机", "50,000g")
                .contains("赌场", "神秘的齐", "09:00-23:50")
                .contains("法师塔", "幻觉神龛", "500g")
                .contains("浣熊商店", "胡萝卜种子", "以物换物")
                .doesNotContain("夏季鱼类", "工具升级", "果树怎么种", "居民位置");
    }

    @Test
    void typedShopIntentRetrievesFestivalShopEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.SHOP, "草莓种子在哪里买"),
                intent(StardewGuideIntent.SHOP, "沙漠节换什么"),
                intent(StardewGuideIntent.SHOP, "星之果实展览会在哪里买"),
                intent(StardewGuideIntent.SHOP, "万灵节稀有稻草人2多少钱")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("草莓种子、沙漠节、展览会星之果实和万灵节稀有稻草人怎么买", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.SHOP);
        assertThat(joinAnswers(evidence))
                .contains("复活节商店", "草莓种子", "100g")
                .contains("沙漠节商店", "卡利科三花蛋", "魔法糖冰棍")
                .contains("星露谷展览会商店", "星之果实", "2,000 星星币")
                .contains("万灵节商店", "稀有稻草人 #2", "5,000g")
                .doesNotContain("夏季鱼类", "工具升级", "居民位置", "鸡舍");
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
    void typedGuideIntentRetrievesForgeEnchantingEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.GUIDE, "银河剑怎么锻造"),
                intent(StardewGuideIntent.GUIDE, "工具附魔哪个好"),
                intent(StardewGuideIntent.GUIDE, "戒指合成怎么做")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("银河剑锻造、工具附魔和戒指合成怎么弄", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("火山锻造与附魔", "火山地牢第 10 层", "10/15/20 个火山晶石")
                .contains("五彩碎片 x1", "工具附魔会在工具升级后保留", "两个不同戒指")
                .doesNotContain("工具升级总览", "春季作物：", "夏季鱼类", "在哪里购买");
    }

    @Test
    void typedGuideIntentRetrievesMasteryEvidenceWithoutCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.GUIDE, "精通先选哪个"),
                intent(StardewGuideIntent.GUIDE, "高级铱金鱼竿怎么获得"),
                intent(StardewGuideIntent.GUIDE, "挑战鱼饵怎么做")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("精通先选哪个，高级铱金鱼竿和挑战鱼饵怎么弄", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("精通系统", "精通洞穴", "高级铱金鱼竿", "挑战鱼饵")
                .contains("10,000 / 15,000 / 20,000 / 25,000 / 30,000", "骨头碎片 x5", "苔藓 x2")
                .doesNotContain("夏季鱼类", "鱼类条件", "机器/加工设备", "资源获取方式");
    }

    @Test
    void typedGuideIntentRetrievesTrinketEvidenceWithoutCrossRoutingToMasteryOrResources() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.GUIDE, "小饰品哪个好"),
                intent(StardewGuideIntent.GUIDE, "青蛙蛋刷怪物掉落好吗"),
                intent(StardewGuideIntent.GUIDE, "魔法箭筒铁砧刷什么")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("小饰品哪个好，青蛙蛋和魔法箭筒怎么刷", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .containsOnly(StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .containsOnly("guide");
        assertThat(joinAnswers(evidence))
                .contains("小饰品与铁砧重铸", "战斗精通", "铱锭 x3", "仙女盒", "寒冰法杖")
                .contains("被青蛙吃掉的敌人不会掉落物品", "完美为 0.9 秒冷却", "生成率 4%")
                .doesNotContain("精通系统：", "10,000 / 15,000 / 20,000 / 25,000 / 30,000", "资源获取方式", "工具升级总览");
    }

    @Test
    void typedFishPondIntentRetrievesPondEvidenceWithoutFishOrBuildingCrossRouting() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.FISH_POND, "鲟鱼鱼塘产什么"),
                intent(StardewGuideIntent.FISH_POND, "鱼塘养什么好"),
                intent(StardewGuideIntent.BUILDING, "鱼塘建造材料多少钱")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("鲟鱼鱼塘产什么，鱼塘建造材料多少钱", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.FISH_POND, StardewGuideIntent.BUILDING);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .contains("fish_pond_detail", "fish_pond_available", "building_detail");
        assertThat(joinAnswers(evidence))
                .contains("鲟鱼鱼塘", "1-2 鱼籽", "鱼塘产物与推荐", "岩浆鳗鱼", "黄貂鱼")
                .contains("花费：5,000g", "石头 x200")
                .doesNotContain("夏季鱼类", "鱼类条件", "工具升级总览");
    }

    @Test
    void typedCurrencyResourceAndGuideEvidenceDoesNotCrossRouteToShopOrArtifact() {
        StardewQueryPlan plan = plan(
                intent(StardewGuideIntent.RESOURCE, "星星币怎么刷"),
                intent(StardewGuideIntent.RESOURCE, "齐币在哪里买"),
                intent(StardewGuideIntent.RESOURCE, "金色标签怎么获得"),
                intent(StardewGuideIntent.RESOURCE, "火山晶石怎么用"),
                intent(StardewGuideIntent.GUIDE, "特殊货币有哪些")
        );

        List<StardewGuideEvidence> evidence = retriever.retrieve("特殊货币和星星币、齐币、金色标签怎么处理", plan);

        assertThat(evidence).extracting(StardewGuideEvidence::type)
                .contains(StardewGuideIntent.RESOURCE, StardewGuideIntent.GUIDE);
        assertThat(evidence).extracting(StardewGuideEvidence::intent)
                .contains("resource", "guide");
        assertThat(joinAnswers(evidence))
                .contains("星星币获取方式", "农庄展览", "1,000g 换 100 齐币", "鳟鱼大赛", "特殊货币与兑换物")
                .contains("火山晶石获取方式", "火山地牢")
                .doesNotContain("陶瓷碎片", "星之碎片", "工具升级总览", "星露谷展览会商店：", "火山锻造与附魔");
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
