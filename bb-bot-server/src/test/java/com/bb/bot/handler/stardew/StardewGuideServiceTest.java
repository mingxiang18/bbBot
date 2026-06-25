package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StardewGuideServiceTest {

    private StardewGuideService service;

    @BeforeEach
    void setUp() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        service = new StardewGuideService(repository);
    }

    @Test
    void answersSummerFishQuery() {
        StardewGuideResult result = service.answer("星露谷 夏天能钓什么鱼");

        assertThat(result.getIntent()).isEqualTo("fish_available");
        assertThat(result.getAnswer()).contains("河豚", "金枪鱼", "鲟鱼");
        assertThat(result.getAnswer()).contains("建议");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/鱼");
    }

    @Test
    void filtersFishByWeatherAndLocation() {
        StardewGuideResult result = service.answer("星露谷 夏季雨天海边能钓什么鱼");

        assertThat(result.getAnswer()).contains("红鲷鱼");
        assertThat(result.getAnswer()).doesNotContain("河豚");
    }

    @Test
    void answersBroaderSeasonalFishQueries() {
        StardewGuideResult springOcean = service.answer("星露谷 春季海边能钓什么鱼");
        StardewGuideResult fallNight = service.answer("星露谷 秋季雨天 23:00 能钓什么鱼");
        StardewGuideResult winterMine = service.answer("星露谷 冬季矿井能钓什么鱼");

        assertThat(springOcean.getIntent()).isEqualTo("fish_available");
        assertThat(springOcean.getAnswer()).contains("沙丁鱼", "鳀鱼", "比目鱼");
        assertThat(fallNight.getIntent()).isEqualTo("fish_available");
        assertThat(fallNight.getAnswer()).contains("鳗鱼", "大眼鱼", "午夜鲤鱼");
        assertThat(winterMine.getIntent()).isEqualTo("fish_available");
        assertThat(winterMine.getAnswer()).contains("鬼鱼", "石鱼", "冰柱鱼", "岩浆鳗鱼");
    }

    @Test
    void answersSpecialLocationFishQueries() {
        StardewGuideResult nightMarket = service.answer("星露谷 夜市潜艇能钓什么鱼");
        StardewGuideResult gingerIsland = service.answer("星露谷 姜岛能钓什么鱼");
        StardewGuideResult waterfall = service.answer("星露谷 瀑布能钓什么鱼");

        assertThat(nightMarket.getIntent()).isEqualTo("fish_available");
        assertThat(nightMarket.getAnswer()).contains("午夜鱿鱼", "水滴鱼", "幽灵鱼");
        assertThat(gingerIsland.getIntent()).isEqualTo("fish_available");
        assertThat(gingerIsland.getAnswer()).contains("黄貂鱼", "狮子鱼", "蓝铁饼鱼");
        assertThat(waterfall.getIntent()).isEqualTo("fish_available");
        assertThat(waterfall.getAnswer()).contains("虾虎鱼");
    }

    @Test
    void answersRemainingCommonAndVersion16FishingQueries() {
        StardewGuideResult winterOceanMorning = service.answer("星露谷 冬季海边上午 8点 能钓什么鱼");
        StardewGuideResult winterFreshwater = service.answer("星露谷 冬季河流和山湖能钓什么鱼");
        StardewGuideResult dorado = service.answer("星露谷 dorado 怎么钓");

        assertThat(winterOceanMorning.getIntent()).isEqualTo("fish_available");
        assertThat(winterOceanMorning.getAnswer()).contains("青花鱼", "海参");
        assertThat(winterFreshwater.getIntent()).isEqualTo("fish_available");
        assertThat(winterFreshwater.getAnswer()).contains("蛇齿单线鱼");
        assertThat(dorado.getIntent()).isEqualTo("fish_detail");
        assertThat(dorado.getAnswer()).contains("麻哈脂鲤", "森林河流", "夏季");
    }

    @Test
    void answersFishingJellyQueries() {
        StardewGuideResult all = service.answer("星露谷 三种果冻怎么钓");
        StardewGuideResult sea = service.answer("星露谷 海果冻怎么获取");
        StardewGuideResult cave = service.answer("星露谷 洞穴果冻在哪钓");

        assertThat(all.getIntent()).isEqualTo("fish_available");
        assertThat(all.getAnswer()).contains("海果冻", "河果冻", "洞穴果冻", "鱼熏机");
        assertThat(sea.getIntent()).isEqualTo("fish_detail");
        assertThat(sea.getAnswer()).contains("海果冻", "海洋", "不算普通鱼");
        assertThat(cave.getIntent()).isEqualTo("fish_detail");
        assertThat(cave.getAnswer()).contains("矿井 20/60/100 层", "运气");
    }

    @Test
    void answersCrabPotFishQueries() {
        StardewGuideResult all = service.answer("星露谷 蟹笼能抓什么");
        StardewGuideResult freshwater = service.answer("星露谷 淡水蟹笼能抓什么");
        StardewGuideResult seawater = service.answer("星露谷 海水蟹笼能抓什么");

        assertThat(all.getIntent()).isEqualTo("fish_available");
        assertThat(all.getAnswer()).contains("龙虾", "小龙虾", "螃蟹", "虾", "蜗牛", "玉黍螺");
        assertThat(freshwater.getAnswer()).contains("小龙虾", "蜗牛", "玉黍螺");
        assertThat(freshwater.getAnswer()).doesNotContain("龙虾：蟹笼海水");
        assertThat(seawater.getAnswer()).contains("龙虾", "蛤", "鸟蛤", "蚌", "牡蛎", "虾");
    }

    @Test
    void answersLegendaryFishQueries() {
        StardewGuideResult result = service.answer("星露谷 传说鱼有哪些");

        assertThat(result.getIntent()).isEqualTo("fish_available");
        assertThat(result.getAnswer()).contains("传说之鱼", "绯红鱼", "鮟鱇鱼", "冰川鱼", "变种鲤鱼");
        assertThat(result.getAnswer()).contains("每个玩家角色通常只能钓起一次");
        assertThat(result.getAnswer()).doesNotContain("传说之鱼二代", "绯红鱼之子");
    }

    @Test
    void answersLegendaryFishIiQueries() {
        StardewGuideResult result = service.answer("星露谷 大家族任务传说鱼有哪些");

        assertThat(result.getIntent()).isEqualTo("fish_available");
        assertThat(result.getAnswer()).contains("传说之鱼二代", "绯红鱼之子", "雌鮟鱇鱼", "小冰川鱼", "放射性鲤鱼");
        assertThat(result.getAnswer()).contains("大家族", "不受原季节/天气限制");
        assertThat(result.getAnswer()).doesNotContain("变种鲤鱼：");
    }

    @Test
    void answersBundleRequirements() {
        StardewGuideResult result = service.answer("星露谷 海鱼收集包需要什么");

        assertThat(result.getIntent()).isEqualTo("bundle");
        assertThat(result.getAnswer()).contains("沙丁鱼", "金枪鱼", "红鲷鱼", "罗非鱼");
        assertThat(result.getAnswer()).contains("奖励");
    }

    @Test
    void answersExpandedCommunityCenterBundleRequirements() {
        StardewGuideResult fallCrops = service.answer("星露谷 秋季作物收集包需要什么");
        StardewGuideResult crabPot = service.answer("星露谷 蟹笼收集包交哪几个");
        StardewGuideResult dye = service.answer("星露谷 布告栏染料收集包需要什么");
        StardewGuideResult vault = service.answer("星露谷 金库一共要多少钱");
        StardewGuideResult missing = service.answer("星露谷 电影院收集包要什么");

        assertThat(fallCrops.getIntent()).isEqualTo("bundle");
        assertThat(fallCrops.getAnswer()).contains("玉米", "茄子", "南瓜", "山药", "蜂房");
        assertThat(crabPot.getIntent()).isEqualTo("bundle");
        assertThat(crabPot.getAnswer()).contains("龙虾", "小龙虾", "螃蟹", "10 选 5", "蟹笼（3）");
        assertThat(dye.getIntent()).isEqualTo("bundle");
        assertThat(dye.getAnswer()).contains("红蘑菇", "海胆", "向日葵", "鸭毛", "红叶卷心菜");
        assertThat(vault.getIntent()).isEqualTo("bundle");
        assertThat(vault.getAnswer()).contains("2,500", "巧克力蛋糕");
        assertThat(missing.getIntent()).isEqualTo("bundle");
        assertThat(missing.getAnswer()).contains("恐龙蛋黄酱", "五彩碎片", "上古水果", "鱼籽酱", "电影院");
    }

    @Test
    void resolvesVillagerLocationWithConditions() {
        StardewGuideResult result = service.answer("星露谷 阿比盖尔 夏季 12日 15:00 晴天在哪");

        assertThat(result.getIntent()).isEqualTo("villager_schedule");
        assertThat(result.getAnswer()).contains("阿比盖尔大概率在");
        assertThat(result.getAnswer()).contains("深山湖边");
        assertThat(result.getAnswer()).contains("夏季普通日程");
    }

    @Test
    void asksForMissingVillagerContext() {
        StardewGuideResult result = service.answer("星露谷 阿比盖尔现在在哪");

        assertThat(result.getIntent()).isEqualTo("villager_schedule");
        assertThat(result.getAnswer()).contains("请至少补充游戏内时间");
    }

    @Test
    void answersVillagerGiftAndBirthdayQuestions() {
        StardewGuideResult result = service.answer("星露谷 阿比盖尔喜欢什么礼物，生日什么时候");

        assertThat(result.getIntent()).isEqualTo("villager_profile");
        assertThat(result.getAnswer()).contains("秋季 13 日", "紫水晶", "河豚", "最爱礼物");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/阿比盖尔");
    }

    @Test
    void answersExpandedVillagerGiftProfiles() {
        StardewGuideResult emily = service.answer("星露谷 艾米丽生日和最爱礼物");
        StardewGuideResult caroline = service.answer("星露谷 卡洛琳喜欢什么");

        assertThat(emily.getIntent()).isEqualTo("villager_profile");
        assertThat(emily.getAnswer()).contains("春季 27 日", "紫水晶", "布料", "羊毛");
        assertThat(caroline.getIntent()).isEqualTo("villager_profile");
        assertThat(caroline.getAnswer()).contains("冬季 7 日", "绿茶", "夏季亮片");
    }

    @Test
    void answersExpandedVillagerSchedulesLocally() {
        StardewGuideResult alex = service.answer("星露谷 亚历克斯 夏季 13日 10:00 在哪");
        StardewGuideResult maru = service.answer("星露谷 玛鲁 星期二 11:00 在哪");
        StardewGuideResult krobus = service.answer("星露谷 科罗布斯 冬季 1日 20:00 在哪");
        StardewGuideResult vincent = service.answer("星露谷 文森特 星期三 12:00 在哪");

        assertThat(alex.getIntent()).isEqualTo("villager_schedule");
        assertThat(alex.getAnswer()).contains("亚历克斯大概率在", "海滩", "夏季普通日程");
        assertThat(maru.getIntent()).isEqualTo("villager_schedule");
        assertThat(maru.getAnswer()).contains("玛鲁大概率在", "哈维的诊所", "诊所工作日");
        assertThat(krobus.getIntent()).isEqualTo("villager_schedule");
        assertThat(krobus.getAnswer()).contains("科罗布斯大概率在", "下水道商店", "常驻日程");
        assertThat(vincent.getIntent()).isEqualTo("villager_schedule");
        assertThat(vincent.getAnswer()).contains("文森特大概率在", "博物馆上课", "上课日程");
    }

    @Test
    void answersGenericFriendshipGuide() {
        StardewGuideResult result = service.answer("星露谷 怎么刷好感和送礼");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("每周最多收 2 份礼物", "生日", "具体人可以问");
    }

    @Test
    void answersResourceGuide() {
        StardewGuideResult result = service.answer("星露谷 硬木怎么获取");

        assertThat(result.getIntent()).isEqualTo("resource");
        assertThat(result.getAnswer()).contains("秘密森林", "钢斧", "推荐");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/硬木");
    }

    @Test
    void answersExpandedResourceGuides() {
        StardewGuideResult coal = service.answer("星露谷 煤炭怎么刷");
        StardewGuideResult clay = service.answer("星露谷 黏土怎么获得");
        StardewGuideResult oakResin = service.answer("星露谷 橡树树脂怎么获得，小桶缺这个");
        StardewGuideResult pondMaterials = service.answer("星露谷 海草和绿藻怎么获取");

        assertThat(coal.getIntent()).isEqualTo("resource");
        assertThat(coal.getAnswer()).contains("煤尘精灵", "木炭窑", "克林特");
        assertThat(clay.getIntent()).isEqualTo("resource");
        assertThat(clay.getAnswer()).contains("锄地", "古物点", "筒仓");
        assertThat(oakResin.getIntent()).isEqualTo("resource");
        assertThat(oakResin.getAnswer()).contains("树液采集器", "橡树", "小桶");
        assertThat(pondMaterials.getIntent()).isEqualTo("resource");
        assertThat(pondMaterials.getAnswer()).contains("海洋钓鱼", "淡水钓鱼", "鱼塘");
    }

    @Test
    void answersMonsterLootResourceGuides() {
        StardewGuideResult coal = service.answer("星露谷 煤尘精灵刷煤炭怎么刷");
        StardewGuideResult solarEssence = service.answer("星露谷 太阳精华哪里刷");
        StardewGuideResult voidEssence = service.answer("星露谷 虚空精华怎么获得");
        StardewGuideResult batWing = service.answer("星露谷 蝙蝠翅膀哪里刷");
        StardewGuideResult slime = service.answer("星露谷 史莱姆泥怎么获得");
        StardewGuideResult bugMeat = service.answer("星露谷 虫肉哪里刷");
        StardewGuideResult boneFragment = service.answer("星露谷 骨头碎片怎么刷");

        assertThat(coal.getIntent()).isEqualTo("resource");
        assertThat(coal.getAnswer()).contains("煤炭获取方式", "煤尘精灵", "41-79", "50%", "木炭窑");
        assertThat(solarEssence.getIntent()).isEqualTo("resource");
        assertThat(solarEssence.getAnswer()).contains("太阳精华获取方式", "科罗布斯", "80g", "幽灵", "木乃伊");
        assertThat(voidEssence.getIntent()).isEqualTo("resource");
        assertThat(voidEssence.getAnswer()).contains("虚空精华获取方式", "暗影", "科罗布斯", "100g", "虚空鲑鱼");
        assertThat(batWing.getIntent()).isEqualTo("resource");
        assertThat(batWing.getAnswer()).contains("蝙蝠翅膀获取方式", "31-39", "41-79", "81-119", "科罗布斯");
        assertThat(slime.getIntent()).isEqualTo("resource");
        assertThat(slime.getAnswer()).contains("史莱姆泥获取方式", "史莱姆", "科罗布斯", "每周一", "史莱姆鱼");
        assertThat(bugMeat.getIntent()).isEqualTo("resource");
        assertThat(bugMeat.getAnswer()).contains("虫肉获取方式", "矿井 1-39", "15-29", "鱼饵");
        assertThat(boneFragment.getIntent()).isEqualTo("resource");
        assertThat(boneFragment.getAnswer()).contains("骨头碎片获取方式", "71-79", "姜岛", "骨头矿点", "骨头磨坊");
    }

    @Test
    void answersRareItemResourceGuides() {
        StardewGuideResult dinosaurEgg = service.answer("星露谷 恐龙蛋怎么获得");
        StardewGuideResult dinosaurMayonnaise = service.answer("星露谷 恐龙蛋黄酱怎么做");
        StardewGuideResult dwarfScroll = service.answer("星露谷 矮人卷轴在哪刷");
        StardewGuideResult rabbitFoot = service.answer("星露谷 兔子的脚怎么获得");
        StardewGuideResult caviar = service.answer("星露谷 鱼籽酱怎么做");

        assertThat(dinosaurEgg.getIntent()).isEqualTo("resource");
        assertThat(dinosaurEgg.getAnswer()).contains("史前层", "孵化器", "12 天");
        assertThat(dinosaurMayonnaise.getIntent()).isEqualTo("resource");
        assertThat(dinosaurMayonnaise.getAnswer()).contains("蛋黄酱机", "失踪的收集包", "先孵化");
        assertThat(dwarfScroll.getIntent()).isEqualTo("resource");
        assertThat(dwarfScroll.getAnswer()).contains("矮人语教程", "95 层", "四卷");
        assertThat(rabbitFoot.getIntent()).isEqualTo("resource");
        assertThat(rabbitFoot.getAnswer()).contains("兔子", "飞蛇", "0.8%", "魔法师收集包");
        assertThat(caviar.getIntent()).isEqualTo("resource");
        assertThat(caviar.getAnswer()).contains("鲟鱼", "鱼塘", "罐头瓶", "6000 分钟");
    }

    @Test
    void answersLateGameAndSpecialOrderResourceGuides() {
        StardewGuideResult redCabbage = service.answer("星露谷 红叶卷心菜第一年怎么拿");
        StardewGuideResult ectoplasm = service.answer("星露谷 灵外质怎么刷");
        StardewGuideResult radioactiveOre = service.answer("星露谷 放射性矿石怎么获得");
        StardewGuideResult dragonTooth = service.answer("星露谷 龙牙哪里刷");

        assertThat(redCabbage.getIntent()).isEqualTo("resource");
        assertThat(redCabbage.getAnswer()).contains("旅行货车", "确保第一年完成", "染料收集包");
        assertThat(ectoplasm.getIntent()).isEqualTo("resource");
        assertThat(ectoplasm.getAnswer()).contains("奇特物质", "幽灵", "任务外不用刷");
        assertThat(radioactiveOre.getIntent()).isEqualTo("resource");
        assertThat(radioactiveOre.getAnswer()).contains("危险矿井", "齐先生", "放射性矿点");
        assertThat(dragonTooth.getIntent()).isEqualTo("resource");
        assertThat(dragonTooth.getAnswer()).contains("火山地牢", "熔岩潜伏怪", "黄貂鱼鱼塘");
    }

    @Test
    void answersMuseumArtifactMineralAndGeodeQuestions() {
        StardewGuideResult museum = service.answer("星露谷 博物馆缺古物和矿物怎么补");
        StardewGuideResult omniGeode = service.answer("星露谷 万象晶球怎么刷，开还是换古物宝藏");
        StardewGuideResult artifactTrove = service.answer("星露谷 古物宝藏怎么获得");
        StardewGuideResult dwarfGadget = service.answer("星露谷 矮人小工具怎么获得");
        StardewGuideResult diamond = service.answer("星露谷 钻石怎么获得");
        StardewGuideResult goldenRelic = service.answer("星露谷 黄金遗物哪里刷");

        assertThat(museum.getIntent()).isEqualTo("guide");
        assertThat(museum.getAnswer()).contains("95 件", "42 件古物", "53 件矿物", "星之果实");
        assertThat(omniGeode.getIntent()).isEqualTo("resource");
        assertThat(omniGeode.getAnswer()).contains("骷髅洞穴", "古物宝藏", "前期优先打开");
        assertThat(artifactTrove.getIntent()).isEqualTo("resource");
        assertThat(artifactTrove.getAnswer()).contains("沙漠商人", "万象晶球", "补缺失古物");
        assertThat(dwarfGadget.getIntent()).isEqualTo("resource");
        assertThat(dwarfGadget.getAnswer()).contains("40-79 层", "岩浆晶球", "农场电脑");
        assertThat(diamond.getIntent()).isEqualTo("resource");
        assertThat(diamond.getAnswer()).contains("钻石矿点", "宝石复制机", "礼物");
        assertThat(goldenRelic.getIntent()).isEqualTo("resource");
        assertThat(goldenRelic.getAnswer()).contains("沙漠", "古物宝藏", "鱼塘任务");
    }

    @Test
    void answersAnimalCareAndAnimalProductQuestions() {
        StardewGuideResult care = service.answer("星露谷 动物怎么养，怎么提高心情和好感");
        StardewGuideResult milk = service.answer("星露谷 大壶牛奶怎么获得");
        StardewGuideResult wool = service.answer("星露谷 羊毛怎么获得");
        StardewGuideResult voidEgg = service.answer("星露谷 虚空蛋怎么获得");
        StardewGuideResult ostrichEgg = service.answer("星露谷 鸵鸟蛋拿到后怎么办");
        StardewGuideResult coop = service.answer("星露谷 鸡舍升级材料");

        assertThat(care.getIntent()).isEqualTo("guide");
        assertThat(care.getAnswer()).contains("每天摸动物", "干草", "金色动物饼干");
        assertThat(milk.getIntent()).isEqualTo("resource");
        assertThat(milk.getAnswer()).contains("奶牛", "挤奶桶", "好感");
        assertThat(wool.getIntent()).isEqualTo("resource");
        assertThat(wool.getAnswer()).contains("绵羊", "兔子", "织布机");
        assertThat(voidEgg.getIntent()).isEqualTo("resource");
        assertThat(voidEgg.getAnswer()).contains("科罗布斯", "虚空鸡", "孵化器");
        assertThat(ostrichEgg.getIntent()).isEqualTo("resource");
        assertThat(ostrichEgg.getAnswer()).contains("姜岛", "鸵鸟孵化器", "优先考虑孵化");
        assertThat(coop.getIntent()).isEqualTo("building_detail");
        assertThat(coop.getAnswer()).contains("鸡舍", "木材", "石头");
    }

    @Test
    void answersFruitTreeAndFruitQuestions() {
        StardewGuideResult fruitTrees = service.answer("星露谷 果树怎么种，温室能种吗");
        StardewGuideResult apple = service.answer("星露谷 苹果怎么获得，收集包要几个");
        StardewGuideResult pomegranate = service.answer("星露谷 石榴怎么获得");
        StardewGuideResult banana = service.answer("星露谷 香蕉怎么获得");

        assertThat(fruitTrees.getIntent()).isEqualTo("guide");
        assertThat(fruitTrees.getAnswer()).contains("28 天", "3x3", "温室", "姜岛");
        assertThat(apple.getIntent()).isEqualTo("resource");
        assertThat(apple.getAnswer()).contains("苹果树苗", "秋季", "饲料收集包需要 3 个苹果");
        assertThat(pomegranate.getIntent()).isEqualTo("resource");
        assertThat(pomegranate.getAnswer()).contains("石榴树苗", "秋季", "魔法师收集包");
        assertThat(banana.getIntent()).isEqualTo("resource");
        assertThat(banana.getAnswer()).contains("香蕉树苗", "姜岛", "全年结果");
    }

    @Test
    void answersCommonShopAndPurchaseQuestions() {
        StardewGuideResult backpack = service.answer("星露谷 背包升级多少钱");
        StardewGuideResult sprinkler = service.answer("星露谷 铱制洒水器在哪里买");
        StardewGuideResult hay = service.answer("星露谷 干草在哪里买");

        assertThat(backpack.getIntent()).isEqualTo("shop_item");
        assertThat(backpack.getAnswer()).contains("皮埃尔", "大背包", "2,000g", "豪华背包", "10,000g");
        assertThat(sprinkler.getIntent()).isEqualTo("shop_item");
        assertThat(sprinkler.getAnswer()).contains("科罗布斯", "铱制洒水器", "10,000g", "每周五");
        assertThat(hay.getIntent()).isEqualTo("shop_item");
        assertThat(hay.getAnswer()).contains("玛妮", "干草", "50g", "动物");
    }

    @Test
    void answersCommonMerchantSchedulesAndTrades() {
        StardewGuideResult bookseller = service.answer("星露谷 书商什么时候来");
        StardewGuideResult desertTrader = service.answer("星露谷 沙漠商人楼梯怎么换");
        StardewGuideResult robin = service.answer("星露谷 罗宾商店几点开门");

        assertThat(bookseller.getIntent()).isEqualTo("shop");
        assertThat(bookseller.getAnswer()).contains("每个季节随机来访 2 天", "日历", "Joja");
        assertThat(desertTrader.getIntent()).isEqualTo("shop_item");
        assertThat(desertTrader.getAnswer()).contains("沙漠商人", "楼梯", "翡翠 x1", "星期日");
        assertThat(robin.getIntent()).isEqualTo("shop");
        assertThat(robin.getAnswer()).contains("木匠", "09:00-17:00", "周二");
    }

    @Test
    void answersToolUpgradeCosts() {
        StardewGuideResult result = service.answer("星露谷 斧头升级需要什么条件和金钱");

        assertThat(result.getIntent()).isEqualTo("tool_upgrade_detail");
        assertThat(result.getAnswer()).contains("铜斧", "2,000g", "铜锭 x5");
        assertThat(result.getAnswer()).contains("钢斧", "5,000g", "铁锭 x5");
        assertThat(result.getAnswer()).contains("秘密森林");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Axe");
    }

    @Test
    void answersGenericToolUpgradeRule() {
        StardewGuideResult result = service.answer("星露谷 工具升级多少钱");

        assertThat(result.getIntent()).isEqualTo("tool_upgrade_list");
        assertThat(result.getAnswer()).contains("斧头", "铜斧 2,000g + 铜锭 x5");
        assertThat(result.getAnswer()).contains("喷壶", "铱喷壶 25,000g + 铱锭 x5");
        assertThat(result.getAnswer()).contains("垃圾桶", "铱垃圾桶 12,500g + 铱锭 x5");
    }

    @Test
    void answersSpecificToolUpgradeTier() {
        StardewGuideResult axe = service.answer("星露谷 钢斧需要什么材料");
        StardewGuideResult rod = service.answer("星露谷 玻璃纤维鱼竿多少钱，怎么解锁");

        assertThat(axe.getIntent()).isEqualTo("tool_upgrade_detail");
        assertThat(axe.getAnswer()).contains("钢斧需要：5,000g + 铁锭 x5", "前置：铜斧", "秘密森林");
        assertThat(rod.getIntent()).isEqualTo("tool_upgrade_detail");
        assertThat(rod.getAnswer()).contains("玻璃纤维鱼竿需要：1,800g", "钓鱼等级 2", "鱼饵");
    }

    @Test
    void answersBuildingMaterials() {
        StardewGuideResult result = service.answer("星露谷 鸡舍升级材料多少钱");

        assertThat(result.getIntent()).isEqualTo("building_detail");
        assertThat(result.getAnswer()).contains("大鸡舍", "10,000g", "木材 x400", "石头 x150");
        assertThat(result.getAnswer()).contains("鸭", "恐龙", "孵化器");
    }

    @Test
    void answersAnimalBuildingUnlocks() {
        StardewGuideResult result = service.answer("星露谷 解锁猪需要什么");

        assertThat(result.getIntent()).isEqualTo("building_detail");
        assertThat(result.getAnswer()).contains("豪华畜棚", "25,000g", "木材 x550", "石头 x300");
        assertThat(result.getAnswer()).contains("猪", "自动喂食", "松露");
    }

    @Test
    void answersUtilityBuildingMaterials() {
        StardewGuideResult result = service.answer("星露谷 筒仓需要什么材料");

        assertThat(result.getIntent()).isEqualTo("building_detail");
        assertThat(result.getAnswer()).contains("筒仓", "100g", "石头 x100", "黏土 x10", "铜锭 x5");
        assertThat(result.getAnswer()).contains("240 份干草");
    }

    @Test
    void answersBuildingListQueries() {
        StardewGuideResult result = service.answer("星露谷 农场建筑有哪些");

        assertThat(result.getIntent()).isEqualTo("building_available");
        assertThat(result.getAnswer()).contains("鸡舍", "畜棚", "筒仓", "鱼塘", "马厩");
    }

    @Test
    void answersLateGameMagicBuildingDetails() {
        StardewGuideResult desert = service.answer("星露谷 沙漠方尖塔需要什么");
        StardewGuideResult goldClock = service.answer("星露谷 黄金钟多少钱，有什么用");
        StardewGuideResult junimo = service.answer("星露谷 祝尼魔小屋多少钱材料");
        StardewGuideResult farmObelisk = service.answer("星露谷 农场方尖塔怎么建");

        assertThat(desert.getIntent()).isEqualTo("building_detail");
        assertThat(desert.getAnswer()).contains("沙漠方尖塔", "1,000,000g", "铱锭 x20", "椰子 x10", "仙人掌果子 x10");
        assertThat(desert.getAnswer()).contains("魔法墨水", "传送到沙漠");
        assertThat(goldClock.getIntent()).isEqualTo("building_detail");
        assertThat(goldClock.getAnswer()).contains("黄金钟", "10,000,000g", "防止农场出现杂物", "防止栅栏腐烂");
        assertThat(junimo.getIntent()).isEqualTo("building_detail");
        assertThat(junimo.getAnswer()).contains("祝尼魔小屋", "20,000g", "石头 x200", "杨桃 x9", "纤维 x100", "收获");
        assertThat(farmObelisk.getIntent()).isEqualTo("building_detail");
        assertThat(farmObelisk.getAnswer()).contains("农场方尖塔", "无固定金币花费", "金核桃 x20", "岛屿农舍", "不能移动");
    }

    @Test
    void answersCommunityUpgradeDetails() {
        StardewGuideResult pamHouse = service.answer("星露谷 潘姆房子社区升级需要什么");
        StardewGuideResult shortcuts = service.answer("星露谷 城镇捷径多少钱");

        assertThat(pamHouse.getIntent()).isEqualTo("building_detail");
        assertThat(pamHouse.getAnswer()).contains("潘姆房屋社区升级", "500,000g", "木材 x950", "房屋完全升级", "社区中心");
        assertThat(pamHouse.getAnswer()).contains("3 天", "多人游戏中只有主机玩家");
        assertThat(shortcuts.getIntent()).isEqualTo("building_detail");
        assertThat(shortcuts.getAnswer()).contains("城镇捷径社区升级", "300,000g", "完成第一次社区升级", "3 天");
        assertThat(shortcuts.getAnswer()).contains("煤矿森林到海滩捷径", "巴士站到后山捷径");
    }

    @Test
    void answersLateGameBuildingCategoryLists() {
        StardewGuideResult magic = service.answer("星露谷 魔法建筑有哪些");
        StardewGuideResult community = service.answer("星露谷 社区升级有哪些");

        assertThat(magic.getIntent()).isEqualTo("building_available");
        assertThat(magic.getAnswer()).contains("可建/可升级建筑（魔法/后期）", "祝尼魔小屋", "沙漠方尖塔", "黄金钟", "农场方尖塔");
        assertThat(community.getIntent()).isEqualTo("building_available");
        assertThat(community.getAnswer()).contains("可建/可升级建筑（社区升级）", "潘姆房屋社区升级", "城镇捷径社区升级");
    }

    @Test
    void answersMachineCraftingDetails() {
        StardewGuideResult keg = service.answer("星露谷 小桶怎么做，酿酒收益怎么样");
        StardewGuideResult smoker = service.answer("星露谷 鱼熏机需要什么材料");

        assertThat(keg.getIntent()).isEqualTo("machine_detail");
        assertThat(keg.getAnswer()).contains("耕种 8 级", "木材 x30", "铜锭 x1", "铁锭 x1", "橡树树脂 x1");
        assertThat(keg.getAnswer()).contains("果酒约为原料基础价 x3", "远古水果", "杨桃");
        assertThat(smoker.getIntent()).isEqualTo("machine_detail");
        assertThat(smoker.getAnswer()).contains("威利鱼店", "10,000g", "硬木 x10", "海果冻 x1", "河果冻 x1", "洞穴果冻 x1");
        assertThat(smoker.getAnswer()).contains("售价为原鱼价格 x2", "50 分钟");
    }

    @Test
    void answersVersion16MachineDetails() {
        StardewGuideResult dehydrator = service.answer("星露谷 脱水机怎么做，葡萄干怎么做");
        StardewGuideResult baitMaker = service.answer("星露谷 诱饵制造机怎么做");
        StardewGuideResult mushroomLog = service.answer("星露谷 蘑菇木桩怎么种蘑菇");

        assertThat(dehydrator.getIntent()).isEqualTo("machine_detail");
        assertThat(dehydrator.getAnswer()).contains("皮埃尔", "木材 x30", "黏土 x2", "火水晶 x1", "葡萄干");
        assertThat(baitMaker.getIntent()).isEqualTo("machine_detail");
        assertThat(baitMaker.getAnswer()).contains("钓鱼 6 级", "铁锭 x3", "珊瑚 x3", "海胆 x1", "定向鱼饵");
        assertThat(mushroomLog.getIntent()).isEqualTo("machine_detail");
        assertThat(mushroomLog.getAnswer()).contains("觅食 4 级", "硬木 x10", "苔藓 x10", "7x7", "雨天");
    }

    @Test
    void answersExpandedCraftingDeviceDetails() {
        StardewGuideResult sprinkler = service.answer("星露谷 优质洒水器怎么做");
        StardewGuideResult staircase = service.answer("星露谷 楼梯怎么做，骷髅洞穴跳层用");
        StardewGuideResult bomb = service.answer("星露谷 炸弹怎么做");
        StardewGuideResult chest = service.answer("星露谷 大箱子怎么做");

        assertThat(sprinkler.getIntent()).isEqualTo("machine_detail");
        assertThat(sprinkler.getAnswer()).contains("耕种 6 级", "铁锭 x1", "金锭 x1", "精炼石英 x1", "8 格");
        assertThat(staircase.getIntent()).isEqualTo("machine_detail");
        assertThat(staircase.getAnswer()).contains("采矿 2 级", "石头 x99", "翡翠", "骷髅洞穴");
        assertThat(bomb.getIntent()).isEqualTo("machine_detail");
        assertThat(bomb.getAnswer()).contains("采矿 6 级", "铁矿石 x4", "煤炭 x1", "半径约 5 格");
        assertThat(chest.getIntent()).isEqualTo("machine_detail");
        assertThat(chest.getAnswer()).contains("木匠的商店", "木材 x120", "铜锭 x2", "两倍");
    }

    @Test
    void answersLateGameAndUtilityCraftingDevices() {
        StardewGuideResult heavyFurnace = service.answer("星露谷 重型熔炉需要什么材料");
        StardewGuideResult solarPanel = service.answer("星露谷 太阳能板怎么做，能产电池吗");
        StardewGuideResult slimePress = service.answer("星露谷 史莱姆蛋压制机怎么做");
        StardewGuideResult geodeCrusher = service.answer("星露谷 晶球破开器怎么做");

        assertThat(heavyFurnace.getIntent()).isEqualTo("machine_detail");
        assertThat(heavyFurnace.getAnswer()).contains("采矿精通", "熔炉 x2", "铁锭 x3", "石头 x50");
        assertThat(solarPanel.getIntent()).isEqualTo("machine_detail");
        assertThat(solarPanel.getAnswer()).contains("卡洛琳特别订单", "精炼石英 x10", "铁锭 x5", "金锭 x5", "电池组");
        assertThat(slimePress.getIntent()).isEqualTo("machine_detail");
        assertThat(slimePress.getAnswer()).contains("战斗 6 级", "煤炭 x25", "火水晶 x1", "电池组 x1", "史莱姆蛋");
        assertThat(geodeCrusher.getIntent()).isEqualTo("machine_detail");
        assertThat(geodeCrusher.getAnswer()).contains("克林特特别订单", "金锭 x2", "石头 x50", "钻石 x1");
    }

    @Test
    void answersCraftingDeviceCategoryLists() {
        StardewGuideResult irrigation = service.answer("星露谷 洒水设备有哪些");
        StardewGuideResult mining = service.answer("星露谷 矿洞设备有哪些");
        StardewGuideResult storage = service.answer("星露谷 储物设备有哪些");

        assertThat(irrigation.getIntent()).isEqualTo("machine_available");
        assertThat(irrigation.getAnswer()).contains("洒水/灌溉", "洒水器", "优质洒水器", "铱制洒水器");
        assertThat(mining.getIntent()).isEqualTo("machine_available");
        assertThat(mining.getAnswer()).contains("矿洞/炸弹", "樱桃炸弹", "炸弹", "超级炸弹", "楼梯");
        assertThat(storage.getIntent()).isEqualTo("machine_available");
        assertThat(storage.getAnswer()).contains("储物/标记", "箱子", "大箱子", "木牌");
    }

    @Test
    void answersFertilizerTotemAndConsumableCraftingDetails() {
        StardewGuideResult speedGro = service.answer("星露谷 高级生长激素怎么做");
        StardewGuideResult rainTotem = service.answer("星露谷 雨水图腾怎么做");
        StardewGuideResult monsterMusk = service.answer("星露谷 怪物香水怎么做");

        assertThat(speedGro.getIntent()).isEqualTo("machine_detail");
        assertThat(speedGro.getAnswer()).contains("高级生长激素", "耕种 8 级", "橡树树脂 x1", "骨头碎片 x5", "25%");
        assertThat(rainTotem.getIntent()).isEqualTo("machine_detail");
        assertThat(rainTotem.getAnswer()).contains("雨水图腾", "觅食 9 级", "硬木 x1", "松露油 x1", "松焦油 x5", "明天下雨");
        assertThat(monsterMusk.getIntent()).isEqualTo("machine_detail");
        assertThat(monsterMusk.getAnswer()).contains("怪物香水", "法师特别订单", "蝙蝠翅膀 x30", "史莱姆 x30", "提高怪物生成量");
    }

    @Test
    void answersRingCraftingDetails() {
        StardewGuideResult iridiumBand = service.answer("星露谷 铱环怎么做");
        StardewGuideResult glowstoneRing = service.answer("星露谷 光辉戒指怎么做");

        assertThat(iridiumBand.getIntent()).isEqualTo("machine_detail");
        assertThat(iridiumBand.getAnswer()).contains("铱环", "战斗 9 级", "铱锭 x5", "太阳精华 x50", "虚空精华 x50", "攻击伤害 +10%");
        assertThat(glowstoneRing.getIntent()).isEqualTo("machine_detail");
        assertThat(glowstoneRing.getAnswer()).contains("光辉戒指", "采矿 4 级", "太阳精华 x5", "铁锭 x5", "增加拾取范围");
    }

    @Test
    void answersCraftingConsumableCategoryLists() {
        StardewGuideResult fertilizers = service.answer("星露谷 肥料有哪些");
        StardewGuideResult totems = service.answer("星露谷 传送图腾有哪些");
        StardewGuideResult rings = service.answer("星露谷 戒指有哪些");

        assertThat(fertilizers.getIntent()).isEqualTo("machine_available");
        assertThat(fertilizers.getAnswer()).contains("肥料/土壤", "基础肥料", "高级生长激素", "树肥");
        assertThat(totems.getIntent()).isEqualTo("machine_available");
        assertThat(totems.getAnswer()).contains("图腾/传送", "海滩传送图腾", "农场传送图腾", "雨水图腾");
        assertThat(rings.getIntent()).isEqualTo("machine_available");
        assertThat(rings.getAnswer()).contains("戒指", "坚固戒指", "光辉戒指", "铱环");
    }

    @Test
    void answersFishingBaitTackleAndCrabPotCraftingDetails() {
        StardewGuideResult magicBait = service.answer("星露谷 魔法鱼饵怎么做");
        StardewGuideResult trapBobber = service.answer("星露谷 陷阱浮标怎么做");
        StardewGuideResult crabPot = service.answer("星露谷 蟹笼怎么做");

        assertThat(magicBait.getIntent()).isEqualTo("machine_detail");
        assertThat(magicBait.getAnswer()).contains("魔法鱼饵", "齐先生", "放射性矿石 x1", "虫肉 x3", "任意季节/时间/天气");
        assertThat(trapBobber.getIntent()).isEqualTo("machine_detail");
        assertThat(trapBobber.getAnswer()).contains("陷阱浮标", "钓鱼 6 级", "铜锭 x1", "树液 x10", "逃脱速度变慢");
        assertThat(crabPot.getIntent()).isEqualTo("machine_detail");
        assertThat(crabPot.getAnswer()).contains("蟹笼", "钓鱼 3 级", "木材 x40", "铁锭 x3", "捕猎者职业");
    }

    @Test
    void answersFishingEquipmentCategoryList() {
        StardewGuideResult tackle = service.answer("星露谷 钓具有哪些");
        StardewGuideResult bait = service.answer("星露谷 鱼饵有哪些");

        assertThat(tackle.getIntent()).isEqualTo("machine_available");
        assertThat(tackle.getAnswer()).contains("钓鱼装备", "陷阱浮标", "声呐浮标", "寻宝器", "倒刺钩");
        assertThat(bait.getIntent()).isEqualTo("machine_available");
        assertThat(bait.getAnswer()).contains("钓鱼装备", "鱼饵", "高级鱼饵", "野性鱼饵", "魔法鱼饵", "挑战鱼饵");
    }

    @Test
    void keepsCrabPotCatchQueriesOnFishRouteAfterCrabPotCraftableIsAdded() {
        StardewGuideResult catchList = service.answer("星露谷 蟹笼能抓什么");

        assertThat(catchList.getIntent()).isEqualTo("fish_available");
        assertThat(catchList.getAnswer()).contains("龙虾", "小龙虾", "螃蟹", "虾", "蜗牛", "玉黍螺");
        assertThat(catchList.getAnswer()).doesNotContain("钓鱼 3 级", "木材 x40", "铁锭 x3");
    }

    @Test
    void answersMachineListQueriesAndKeepsResourceRouting() {
        StardewGuideResult machines = service.answer("星露谷 加工机器有哪些");
        StardewGuideResult battery = service.answer("星露谷 电池组怎么获得");

        assertThat(machines.getIntent()).isEqualTo("machine_available");
        assertThat(machines.getAnswer()).contains("小桶", "罐头瓶", "蛋黄酱机", "鱼熏机", "脱水机");
        assertThat(battery.getIntent()).isEqualTo("resource");
        assertThat(battery.getAnswer()).contains("避雷针", "太阳能板");
    }

    @Test
    void answersCropRecommendation() {
        StardewGuideResult result = service.answer("星露谷 夏季种什么收益好");

        assertThat(result.getIntent()).isEqualTo("crop_available");
        assertThat(result.getAnswer()).contains("杨桃", "蓝莓", "甜瓜", "啤酒花");
        assertThat(result.getAnswer()).contains("g/天", "夏季");
    }

    @Test
    void answersSpecificCropDetails() {
        StardewGuideResult blueberry = service.answer("星露谷 蓝莓几天成熟，收益怎么样");
        StardewGuideResult parsnip = service.answer("星露谷 防风草要不要留收集包");
        StardewGuideResult ancientFruit = service.answer("星露谷 远古水果适合温室吗");

        assertThat(blueberry.getIntent()).isEqualTo("crop_detail");
        assertThat(blueberry.getAnswer()).contains("13 天", "每 4 天再收", "夏季作物收集包");
        assertThat(parsnip.getIntent()).isEqualTo("crop_detail");
        assertThat(parsnip.getAnswer()).contains("春季作物收集包", "高品质作物收集包", "5 个金星");
        assertThat(ancientFruit.getIntent()).isEqualTo("crop_detail");
        assertThat(ancientFruit.getAnswer()).contains("温室", "远古水果酒", "每 7 天再收");
    }

    @Test
    void answersSeasonalCropListsAcrossSeasons() {
        StardewGuideResult spring = service.answer("星露谷 春季作物有哪些");
        StardewGuideResult fall = service.answer("星露谷 秋季种什么收益好");
        StardewGuideResult winter = service.answer("星露谷 冬季能种什么作物");

        assertThat(spring.getIntent()).isEqualTo("crop_available");
        assertThat(spring.getAnswer()).contains("草莓", "花椰菜", "防风草");
        assertThat(fall.getIntent()).isEqualTo("crop_available");
        assertThat(fall.getAnswer()).contains("宝石甜莓", "蔓越莓", "南瓜");
        assertThat(winter.getIntent()).isEqualTo("crop_available");
        assertThat(winter.getAnswer()).contains("粉瓜", "1.6 新增冬季作物");
    }

    @Test
    void answersProgressionAndMechanicGuides() {
        StardewGuideResult desert = service.answer("星露谷 沙漠怎么解锁");
        StardewGuideResult greenhouse = service.answer("星露谷 温室怎么修，里面种什么");
        StardewGuideResult skullCavern = service.answer("星露谷 骷髅洞穴怎么准备");

        assertThat(desert.getIntent()).isEqualTo("guide");
        assertThat(desert.getAnswer()).contains("金库", "修复巴士", "杨桃");
        assertThat(greenhouse.getIntent()).isEqualTo("guide");
        assertThat(greenhouse.getAnswer()).contains("茶水间", "远古水果", "杨桃");
        assertThat(skullCavern.getIntent()).isEqualTo("guide");
        assertThat(skullCavern.getAnswer()).contains("头骨钥匙", "炸弹", "楼梯");
    }

    @Test
    void fallsBackToWikiForUnmodeledGuideQuestions() {
        StardewGuideService wikiBacked = new StardewGuideService(repositoryWithData(), (query, maxResults) -> List.of(
                StardewWikiPage.builder()
                        .title("姜岛")
                        .url("https://zh.stardewvalleywiki.com/姜岛")
                        .excerpt("姜岛是位于蕨岛群岛的岛屿。玩家修复威利鱼店后屋的船后可以前往，并在那里解锁火山、农场和金核桃内容。")
                        .build()));

        StardewGuideResult result = wikiBacked.answer("星露谷 姜岛金核桃怎么收集");

        assertThat(result.getIntent()).isEqualTo("wiki_fallback");
        assertThat(result.getAnswer()).contains("我找到这些可能相关的内容", "姜岛", "金核桃");
        assertThat(result.getAnswer()).doesNotContain("官方 Wiki", "本地结构化库", "来源");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/姜岛");
    }

    @Test
    void answersCombatLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 战斗技能如何快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("战斗经验来自击杀怪物");
        assertThat(result.getAnswer()).contains("250 战斗经验", "10 级 15000", "农场怪物只给标准经验的 1/3");
        assertThat(result.getAnswer()).contains("40-79 层", "70-79 层", "骷髅洞穴", "火山地牢");
        assertThat(result.getAnswer()).contains("怪物香水", "块茎拼盘", "战士 -> 野蛮人");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Combat");
    }

    @Test
    void answersSkillProfessionChoice() {
        StardewGuideResult result = service.answer("星露谷 钓鱼职业怎么选");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("渔夫", "垂钓者", "海盗", "蟹笼");
    }

    @Test
    void answersSkillBookAndProfessionResetQuestions() {
        StardewGuideResult books = service.answer("星露谷 技能书在哪里买，书商什么时候来");
        StardewGuideResult reset = service.answer("星露谷 职业怎么重置");

        assertThat(books.getIntent()).isEqualTo("shop_item");
        assertThat(books.getAnswer()).contains("书商", "每个季节随机来访 2 天", "技能书", "星之书");
        assertThat(reset.getIntent()).isEqualTo("guide");
        assertThat(reset.getAnswer()).contains("不确定雕像", "10,000g", "睡觉后");
    }

    @Test
    void answersSkillFoodBuffQuestions() {
        StardewGuideResult result = service.answer("星露谷 骷髅洞穴吃什么料理 buff 好");

        assertThat(result.getIntent()).isEqualTo("cooking_available");
        assertThat(result.getAnswer()).contains("香辣鳗鱼", "幸运午餐", "三倍浓缩咖啡");
        assertThat(result.getAnswer()).contains("速度", "运气", "饮料类速度 buff 可与食物 buff 叠加");
    }

    @Test
    void answersCookingRecipeDetails() {
        StardewGuideResult luckyLunch = service.answer("星露谷 幸运午餐怎么做");
        StardewGuideResult spicyEel = service.answer("星露谷 香辣鳗鱼材料和效果");

        assertThat(luckyLunch.getIntent()).isEqualTo("cooking_recipe");
        assertThat(luckyLunch.getAnswer()).contains("海参 x1", "玉米饼 x1", "蓝爵 x1", "运气 +3");
        assertThat(spicyEel.getIntent()).isEqualTo("cooking_recipe");
        assertThat(spicyEel.getAnswer()).contains("鳗鱼 x1", "辣椒 x1", "速度 +1", "运气 +1", "红宝石兑换");
    }

    @Test
    void answersCookingBuffRecommendationLists() {
        StardewGuideResult fishing = service.answer("星露谷 钓鱼料理有哪些");
        StardewGuideResult combat = service.answer("星露谷 战斗等级低吃什么食物");

        assertThat(fishing.getIntent()).isEqualTo("cooking_available");
        assertThat(fishing.getAnswer()).contains("海泡布丁", "海之菜肴", "龙虾浓汤", "钓鱼 +");
        assertThat(combat.getIntent()).isEqualTo("cooking_available");
        assertThat(combat.getAnswer()).contains("块茎拼盘", "攻击 +3");
    }

    @Test
    void shortFishQueryDoesNotBecomeBundleQuery() {
        StardewGuideResult result = service.answer("星露谷 鱼");

        assertThat(result.getIntent()).isEqualTo("fish_available");
    }

    private StardewKnowledgeRepository repositoryWithData() {
        StardewKnowledgeRepository repository = new StardewKnowledgeRepository();
        repository.load();
        return repository;
    }
}
