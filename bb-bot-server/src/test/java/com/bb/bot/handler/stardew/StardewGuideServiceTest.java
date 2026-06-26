package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
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
    void answersRemixedCommunityCenterBundleRequirements() {
        StardewGuideResult springCrops = service.answer("星露谷 重混春季作物收集包需要什么");
        StardewGuideResult brewers = service.answer("星露谷 重混酿酒师收集包需要什么");
        StardewGuideResult qualityFish = service.answer("星露谷 重混优质鱼收集包要什么");
        StardewGuideResult adventurer = service.answer("星露谷 重混冒险家收集包交什么");
        StardewGuideResult helper = service.answer("星露谷 重混帮手收集包需要什么");

        assertThat(springCrops.getIntent()).isEqualTo("bundle");
        assertThat(springCrops.getAnswer()).contains("春季作物收集包（重混）", "防风草", "胡萝卜", "6 选 4");
        assertThat(brewers.getIntent()).isEqualTo("bundle");
        assertThat(brewers.getAnswer()).contains("酿酒师收集包（重混）", "蜂蜜酒", "淡啤酒", "绿茶", "小桶");
        assertThat(qualityFish.getIntent()).isEqualTo("bundle");
        assertThat(qualityFish.getAnswer()).contains("优质鱼类收集包（重混）", "大嘴鲈鱼", "金星", "海之菜肴");
        assertThat(adventurer.getIntent()).isEqualTo("bundle");
        assertThat(adventurer.getAnswer()).contains("冒险家收集包（重混）", "骨头碎片", "5 选 4", "小磁铁戒指");
        assertThat(helper.getIntent()).isEqualTo("bundle");
        assertThat(helper.getAnswer()).contains("帮手收集包（重混）", "奖品券", "神秘盒", "星之果实茶");
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
    void answersMonsterDropTableWithoutCrossRoutingToResourceOrBookGuides() {
        StardewGuideResult dustSprite = service.answerEvidence(StardewGuideIntent.MONSTER_DROP, "煤尘精灵掉什么");
        StardewGuideResult serpent = service.answerEvidence(StardewGuideIntent.MONSTER_DROP, "飞蛇在哪刷");
        StardewGuideResult lavaLurk = service.answerEvidence(StardewGuideIntent.MONSTER_DROP, "熔岩潜伏怪掉落");
        StardewGuideResult monsterCompendium = service.answerEvidence(StardewGuideIntent.GUIDE, "怪物图鉴有什么用");
        StardewGuideResult voidEssence = service.answerEvidence(StardewGuideIntent.RESOURCE, "虚空精华哪里刷");

        assertThat(dustSprite.getIntent()).isEqualTo("monster_drop");
        assertThat(dustSprite.getAnswer()).contains("煤尘精灵掉落表", "矿井", "41-79", "煤炭 (50%)");
        assertThat(dustSprite.getAnswer()).doesNotContain("煤炭获取方式");
        assertThat(serpent.getIntent()).isEqualTo("monster_drop");
        assertThat(serpent.getAnswer()).contains("飞蛇掉落表", "骷髅洞穴", "虚空精华 (99%)", "兔子的脚");
        assertThat(lavaLurk.getIntent()).isEqualTo("monster_drop");
        assertThat(lavaLurk.getAnswer()).contains("熔岩潜伏怪掉落表", "火山地牢", "龙牙 (15%)");
        assertThat(monsterCompendium.getIntent()).isEqualTo("book_detail");
        assertThat(monsterCompendium.getAnswer()).contains("怪物图鉴", "双倍战利品");
        assertThat(voidEssence.getIntent()).isEqualTo("resource");
        assertThat(voidEssence.getAnswer()).contains("虚空精华获取方式", "科罗布斯", "飞蛇");
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
    void answersSpecialCurrencyResourcesAndGuideWithoutCrossRouting() {
        StardewGuideResult qiGem = service.answerEvidence(StardewGuideIntent.RESOURCE, "齐钻怎么获得");
        StardewGuideResult walnut = service.answerEvidence(StardewGuideIntent.RESOURCE, "金核桃怎么用");
        StardewGuideResult qiCoin = service.answerEvidence(StardewGuideIntent.RESOURCE, "齐币在哪里买");
        StardewGuideResult starToken = service.answer("星露谷 星星币怎么刷");
        StardewGuideResult calicoEgg = service.answerEvidence(StardewGuideIntent.RESOURCE, "三花蛋换什么");
        StardewGuideResult cinderShard = service.answer("星露谷 火山晶石怎么用");
        StardewGuideResult goldenTag = service.answerEvidence(StardewGuideIntent.RESOURCE, "金色标签怎么获得");
        StardewGuideResult guide = service.answerEvidence(StardewGuideIntent.GUIDE, "特殊货币有哪些");

        assertThat(qiGem.getIntent()).isEqualTo("resource");
        assertThat(qiGem.getAnswer()).contains("齐钻获取方式", "齐先生挑战", "危险矿井", "马笛");
        assertThat(walnut.getAnswer()).contains("金核桃获取方式", "130", "姜岛", "齐先生核桃房");
        assertThat(qiCoin.getAnswer()).contains("齐币获取方式", "1,000g", "100 齐币", "不能兑换回金币");
        assertThat(starToken.getIntent()).isEqualTo("resource");
        assertThat(starToken.getAnswer()).contains("星星币获取方式", "农庄展览", "2,000", "不保留到下一年")
                .doesNotContain("陶瓷碎片", "星之碎片");
        assertThat(calicoEgg.getAnswer()).contains("三花蛋获取方式", "沙漠节", "150g", "节日结束");
        assertThat(cinderShard.getIntent()).isEqualTo("resource");
        assertThat(cinderShard.getAnswer()).contains("火山晶石获取方式", "火山地牢", "黄貂鱼鱼塘", "无限武器")
                .doesNotContain("火山锻造与附魔");
        assertThat(goldenTag.getAnswer()).contains("金色标签获取方式", "鳟鱼大赛", "33%", "跨天保留");
        assertThat(guide.getIntent()).isEqualTo("guide");
        assertThat(guide.getAnswer())
                .contains("特殊货币与兑换物", "齐钻", "金核桃", "齐币", "星星币", "奖券", "三花蛋", "火山晶石", "金色标签")
                .doesNotContain("工具升级总览", "夏季鱼类");
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
    void answersFullMineralResourceQuestionsWithoutMuseumGuideCrossRouting() {
        StardewGuideResult topaz = service.answer("星露谷 黄水晶这个矿物哪里找");
        StardewGuideResult emerald = service.answerEvidence(StardewGuideIntent.RESOURCE, "绿宝石怎么获得");
        StardewGuideResult marble = service.answerEvidence(StardewGuideIntent.RESOURCE, "大理石怎么获得");
        StardewGuideResult bixite = service.answerEvidence(StardewGuideIntent.RESOURCE, "黑方石哪里找");
        StardewGuideResult starShards = service.answerEvidence(StardewGuideIntent.RESOURCE, "陶瓷碎片开哪个晶球");
        StardewGuideResult tigerseye = service.answerEvidence(StardewGuideIntent.RESOURCE, "虎眼石哪里刷");

        assertThat(topaz.getIntent()).isEqualTo("resource");
        assertThat(topaz.getAnswer()).contains("黄水晶获取方式", "黄水晶矿点", "钓鱼宝箱")
                .doesNotContain("博物馆捐赠：");
        assertThat(emerald.getAnswer()).contains("绿宝石获取方式", "绿宝石矿点", "潘妮最爱");
        assertThat(marble.getAnswer()).contains("大理石获取方式", "冰封晶球", "大理石火炬");
        assertThat(bixite.getAnswer()).contains("黑方石获取方式", "岩浆晶球", "黑色史莱姆");
        assertThat(starShards.getAnswer()).contains("陶瓷碎片获取方式", "岩浆晶球", "万象晶球", "星星 T 恤");
        assertThat(tigerseye.getAnswer()).contains("虎眼石获取方式", "山姆最爱", "岩浆晶球");
    }

    @Test
    void answersFullArtifactResourceQuestionsWithoutMuseumGuideCrossRouting() {
        StardewGuideResult ancientDoll = service.answer("星露谷 古代玩偶这个古物怎么获得");
        StardewGuideResult dwarfScrollTwo = service.answerEvidence(StardewGuideIntent.RESOURCE, "矮人卷轴 II 哪里刷");
        StardewGuideResult strangeDollYellow = service.answerEvidence(StardewGuideIntent.RESOURCE, "黄色诡异玩偶怎么拿");
        StardewGuideResult nautilusFossil = service.answerEvidence(StardewGuideIntent.RESOURCE, "鹦鹉螺化石哪里找");
        StardewGuideResult prehistoricVertebra = service.answerEvidence(StardewGuideIntent.RESOURCE, "史前脊骨怎么获得");

        assertThat(ancientDoll.getIntent()).isEqualTo("resource");
        assertThat(ancientDoll.getAnswer()).contains("古代玩偶获取方式", "冬日星盛宴", "古物宝藏")
                .doesNotContain("95 件", "42 件古物", "53 件矿物");
        assertThat(dwarfScrollTwo.getAnswer()).contains("矮人卷轴 II获取方式", "矿井 1-39", "煤尘精灵");
        assertThat(strangeDollYellow.getAnswer()).contains("诡异玩偶（黄）获取方式", "秘密纸条 #18");
        assertThat(nautilusFossil.getAnswer()).contains("鹦鹉螺化石获取方式", "不是冬季沙滩采集物");
        assertThat(prehistoricVertebra.getAnswer()).contains("史前脊骨获取方式", "公交车站", "霸王喷火龙");
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
        StardewGuideResult adventurersGuild = service.answerEvidence(StardewGuideIntent.SHOP, "冒险家公会几点开");
        StardewGuideResult lavaKatana = service.answerEvidence(StardewGuideIntent.SHOP, "熔岩武士刀在哪里买");
        StardewGuideResult bomb = service.answerEvidence(StardewGuideIntent.SHOP, "炸弹在哪里买");
        StardewGuideResult starfruitSeeds = service.answerEvidence(StardewGuideIntent.SHOP, "杨桃种子在哪里买");
        StardewGuideResult bananaSapling = service.answerEvidence(StardewGuideIntent.SHOP, "香蕉树苗怎么换");
        StardewGuideResult horseFlute = service.answerEvidence(StardewGuideIntent.SHOP, "马笛在哪里买");
        StardewGuideResult coffee = service.answerEvidence(StardewGuideIntent.SHOP, "咖啡在哪里买");
        StardewGuideResult energyTonic = service.answerEvidence(StardewGuideIntent.SHOP, "体力药在哪里买");
        StardewGuideResult autoPetter = service.answerEvidence(StardewGuideIntent.SHOP, "自动抚摸机在哪里买");
        StardewGuideResult casino = service.answerEvidence(StardewGuideIntent.SHOP, "赌场怎么进");
        StardewGuideResult islandTotemRecipe = service.answerEvidence(StardewGuideIntent.SHOP, "岛屿图腾配方在哪里买");
        StardewGuideResult shrine = service.answerEvidence(StardewGuideIntent.SHOP, "幻觉神龛多少钱");
        StardewGuideResult iceCream = service.answerEvidence(StardewGuideIntent.SHOP, "冰淇淋在哪里买");
        StardewGuideResult hats = service.answerEvidence(StardewGuideIntent.SHOP, "帽子在哪里买");
        StardewGuideResult carrotSeeds = service.answerEvidence(StardewGuideIntent.SHOP, "胡萝卜种子在哪里买");
        StardewGuideResult strawberrySeeds = service.answerEvidence(StardewGuideIntent.SHOP, "草莓种子在哪里买");
        StardewGuideResult desertFestival = service.answerEvidence(StardewGuideIntent.SHOP, "沙漠节换什么");
        StardewGuideResult fairStardrop = service.answerEvidence(StardewGuideIntent.SHOP, "星之果实展览会在哪里买");
        StardewGuideResult moonlightPudding = service.answerEvidence(StardewGuideIntent.SHOP, "海泡布丁在哪里买");
        StardewGuideResult rarecrowTwo = service.answerEvidence(StardewGuideIntent.SHOP, "万灵节稀有稻草人2多少钱");

        assertThat(bookseller.getIntent()).isEqualTo("shop");
        assertThat(bookseller.getAnswer()).contains("每个季节随机来访 2 天", "日历", "Joja");
        assertThat(desertTrader.getIntent()).isEqualTo("shop_item");
        assertThat(desertTrader.getAnswer()).contains("沙漠商人", "楼梯", "翡翠 x1", "星期日");
        assertThat(robin.getIntent()).isEqualTo("shop");
        assertThat(robin.getAnswer()).contains("木匠", "09:00-17:00", "周二");
        assertThat(adventurersGuild.getIntent()).isEqualTo("shop");
        assertThat(adventurersGuild.getAnswer()).contains("冒险家公会", "14:00-02:00", "入门", "史莱姆");
        assertThat(lavaKatana.getIntent()).isEqualTo("shop_item");
        assertThat(lavaKatana.getAnswer()).contains("冒险家公会", "熔岩武士刀", "25,000g", "矿井底层");
        assertThat(bomb.getIntent()).isEqualTo("shop_item");
        assertThat(bomb.getAnswer()).contains("矮人商店", "炸弹", "1,000g", "常驻");
        assertThat(starfruitSeeds.getIntent()).isEqualTo("shop_item");
        assertThat(starfruitSeeds.getAnswer()).contains("绿洲", "杨桃种子", "400g", "常驻");
        assertThat(bananaSapling.getIntent()).isEqualTo("shop_item");
        assertThat(bananaSapling.getAnswer()).contains("姜岛商人", "香蕉树苗", "龙牙 x5");
        assertThat(horseFlute.getIntent()).isEqualTo("shop_item");
        assertThat(horseFlute.getAnswer()).contains("齐先生核桃房", "马笛", "50 齐钻");
        assertThat(coffee.getIntent()).isEqualTo("shop_item");
        assertThat(coffee.getAnswer()).contains("星之果实餐吧", "咖啡", "300g", "12:00-00:00");
        assertThat(energyTonic.getIntent()).isEqualTo("shop_item");
        assertThat(energyTonic.getAnswer()).contains("哈维", "能量滋补水", "1,000g", "09:00-15:00");
        assertThat(autoPetter.getIntent()).isEqualTo("shop_item");
        assertThat(autoPetter.getAnswer()).contains("Joja", "自动抚摸机", "50,000g", "Joja 路线");
        assertThat(casino.getIntent()).isEqualTo("shop");
        assertThat(casino.getAnswer()).contains("赌场", "09:00-23:50", "神秘的齐", "齐币");
        assertThat(islandTotemRecipe.getIntent()).isEqualTo("shop_item");
        assertThat(islandTotemRecipe.getAnswer()).contains("火山矮人", "岛屿传送图腾配方", "10,000g", "第 5 层");
        assertThat(shrine.getIntent()).isEqualTo("shop_item");
        assertThat(shrine.getAnswer()).contains("法师塔", "幻觉神龛", "500g", "4 心");
        assertThat(iceCream.getIntent()).isEqualTo("shop_item");
        assertThat(iceCream.getAnswer()).contains("冰淇淋摊", "冰淇淋", "250g", "夏季");
        assertThat(hats.getIntent()).isEqualTo("shop_item");
        assertThat(hats.getAnswer()).contains("废弃屋帽子店", "成就帽子", "第一个成就");
        assertThat(carrotSeeds.getIntent()).isEqualTo("shop_item");
        assertThat(carrotSeeds.getAnswer()).contains("浣熊商店", "胡萝卜种子", "以物换物");
        assertThat(strawberrySeeds.getIntent()).isEqualTo("shop_item");
        assertThat(strawberrySeeds.getAnswer()).contains("复活节商店", "草莓种子", "100g", "春季 13 日");
        assertThat(desertFestival.getIntent()).isEqualTo("shop");
        assertThat(desertFestival.getAnswer()).contains("沙漠节商店", "春季 15-17 日", "卡利科三花蛋", "魔法糖冰棍");
        assertThat(fairStardrop.getIntent()).isEqualTo("shop_item");
        assertThat(fairStardrop.getAnswer()).contains("星露谷展览会商店", "星之果实", "2,000 星星币", "只能");
        assertThat(moonlightPudding.getIntent()).isEqualTo("shop_item");
        assertThat(moonlightPudding.getAnswer()).contains("月光水母节商店", "海泡布丁", "5,000g", "夏季 28 日");
        assertThat(rarecrowTwo.getIntent()).isEqualTo("shop_item");
        assertThat(rarecrowTwo.getAnswer()).contains("万灵节商店", "稀有稻草人 #2", "5,000g", "秋季 27 日");
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
                        .title("秘密纸条")
                        .url("https://zh.stardewvalleywiki.com/秘密纸条")
                        .excerpt("秘密纸条 #19 会给出一段方向路线，按路线从指定地点行走可获得隐藏奖励。")
                        .build()));

        StardewGuideResult result = wikiBacked.answer("星露谷 秘密纸条19路线怎么走");

        assertThat(result.getIntent()).isEqualTo("wiki_fallback");
        assertThat(result.getAnswer()).contains("我找到这些可能相关的内容", "秘密纸条", "方向路线");
        assertThat(result.getAnswer()).doesNotContain("官方 Wiki", "本地结构化库", "来源");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/秘密纸条");
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
    void answersFishingLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 钓鱼等级低怎么快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("钓鱼经验来自鱼竿钓到物品", "250 钓鱼经验");
        assertThat(result.getAnswer()).contains("完美钓鱼会让经验 x2.4", "10 级总经验阈值");
        assertThat(result.getAnswer()).contains("训练用鱼竿", "玻璃纤维鱼竿", "铱金鱼竿");
        assertThat(result.getAnswer()).contains("气泡点", "高级鱼饵", "海泡布丁");
        assertThat(result.getAnswer()).contains("渔夫 -> 垂钓者", "不确定雕像");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Fishing");
    }

    @Test
    void answersMiningLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 采矿等级低怎么快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("怪物破坏岩石不给经验", "250 采矿经验");
        assertThat(result.getAnswer()).contains("铜矿点 5", "铁矿点 12", "金矿点 18", "铱矿点 50");
        assertThat(result.getAnswer()).contains("每 5 层电梯", "40-79 层", "80-120 层", "头骨钥匙");
        assertThat(result.getAnswer()).contains("炸弹", "矿工特供", "魔法糖冰棍");
        assertThat(result.getAnswer()).contains("矿工 -> 勘探者", "地质学家 -> 挖掘者");
        assertThat(result.getAnswer()).doesNotContain("怪物破坏都可以");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Mining");
    }

    @Test
    void answersFarmingLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 耕种等级低怎么快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("250 耕种经验", "单纯锄地或浇水不会给耕种经验");
        assertThat(result.getAnswer()).contains("松露给觅食经验", "13 个防风草", "8 个土豆", "5 个花椰菜");
        assertThat(result.getAnswer()).contains("春季", "夏季", "秋季", "洒水器");
        assertThat(result.getAnswer()).contains("农夫午餐", "品质是在收获时决定的");
        assertThat(result.getAnswer()).contains("农耕人 -> 工匠", "不确定雕像");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Farming");
    }

    @Test
    void answersForagingLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 觅食等级低怎么快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("250 觅食经验", "普通地面采集物通常给 7 觅食经验");
        assertThat(result.getAnswer()).contains("砍倒一棵树给 14 觅食经验", "大型树桩和大圆木给 25");
        assertThat(result.getAnswer()).contains("秘密森林", "6 个大型树桩", "150 觅食经验");
        assertThat(result.getAnswer()).contains("冬季野种子", "炸弹炸倒树不给觅食经验");
        assertThat(result.getAnswer()).contains("煎饼", "觅食汉堡", "热带咖喱");
        assertThat(result.getAnswer()).contains("收集者 -> 植物学家", "不确定雕像");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Foraging");
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
        assertThat(books.getAnswer()).contains("5,000-10,000g", "15,000g", "价格目录", "风之道");
        assertThat(reset.getIntent()).isEqualTo("guide");
        assertThat(reset.getAnswer()).contains("不确定雕像", "10,000g", "睡觉后");
    }

    @Test
    void answersBookEffectsAndPrioritiesLocally() {
        StardewGuideResult priceCatalogue = service.answer("星露谷 价格目录有什么用，值得买吗");
        StardewGuideResult starBook = service.answer("星露谷 星之书有什么用");
        StardewGuideResult cookbook = service.answer("星露谷 酱料女皇食谱怎么解锁");
        StardewGuideResult monsterCompendium = service.answerEvidence(StardewGuideIntent.GUIDE, "怪物图鉴有什么用，在哪里买");
        StardewGuideResult combatQuarterly = service.answerEvidence(StardewGuideIntent.GUIDE, "战斗季刊有什么用");
        StardewGuideResult multipleBooks = service.answerEvidence(StardewGuideIntent.GUIDE, "价格目录有什么用，星之书有什么用");

        assertThat(priceCatalogue.getIntent()).isEqualTo("book_detail");
        assertThat(priceCatalogue.getAnswer()).contains("价格目录", "查看物品的出售价值", "书商固定出售", "3,000g");
        assertThat(priceCatalogue.getAnswer()).contains("前期优先买");
        assertThat(starBook.getIntent()).isEqualTo("book_detail");
        assertThat(starBook.getAnswer()).contains("所有技能各获得 250 经验", "1,125 精通点", "15,000g");
        assertThat(cookbook.getIntent()).isEqualTo("book_detail");
        assertThat(cookbook.getAnswer()).contains("酱料女皇食谱", "100 个金核桃", "50,000g", "学会所有尚未掌握的酱料女皇电视食谱");
        assertThat(cookbook.getSourceUrls()).contains("https://stardewvalleywiki.com/Books");
        assertThat(monsterCompendium.getAnswer()).contains("怪物图鉴", "双倍战利品", "第 3 年", "20,000g");
        assertThat(combatQuarterly.getAnswer()).contains("战斗季刊", "250 战斗经验", "书商随机技能书库存");
        assertThat(multipleBooks.getAnswer()).contains("书籍对照", "价格目录", "星之书")
                .doesNotContain("夏季鱼类", "鸡舍升级");
    }

    @Test
    void answersSkillFoodBuffQuestions() {
        StardewGuideResult result = service.answer("星露谷 骷髅洞穴吃什么料理 buff 好");

        assertThat(result.getIntent()).isEqualTo("cooking_available");
        assertThat(result.getAnswer()).contains("香辣鳗鱼", "幸运午餐", "三倍浓缩咖啡");
        assertThat(result.getAnswer()).contains("速度", "运气", "约 7 分钟", "约 4 分钟");
        assertThat(result.getAnswer()).contains("饮料类速度 buff 可与食物 buff 叠加");
    }

    @Test
    void answersFoodBuffStackingRulesLocally() {
        StardewGuideResult result = service.answer("星露谷 料理buff和饮料buff怎么叠加，会互相覆盖吗");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("只能有 1 组食物 buff 和 1 组饮料 buff");
        assertThat(result.getAnswer()).contains("姜汁汽水是饮料类运气 +1", "会覆盖咖啡/三倍浓缩咖啡");
        assertThat(result.getAnswer()).contains("速度、运气和最大体力可以通过食物 + 饮料叠加");
        assertThat(result.getAnswer()).contains("不要连续吃不同 buff 食物");
        assertThat(result.getSourceUrls()).contains("https://stardewvalleywiki.com/Buffs");
    }

    @Test
    void answersForgeEnchantingAndInfinityWeaponGuidesLocally() {
        StardewGuideResult forge = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 银河剑怎么锻造");
        StardewGuideResult enchant = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 工具附魔哪个好");
        StardewGuideResult infinity = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 无限武器怎么做，银河之魂怎么用");
        StardewGuideResult rings = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 戒指合成怎么做");
        StardewGuideResult legacyForge = service.answer("星露谷 银河剑怎么锻造");

        assertThat(forge.getIntent()).isEqualTo("guide");
        assertThat(forge.getAnswer()).contains("火山地牢第 10 层", "10/15/20 个火山晶石", "红宝石", "绿宝石", "钻石");
        assertThat(enchant.getIntent()).isEqualTo("guide");
        assertThat(enchant.getAnswer()).contains("五彩碎片 x1", "火山晶石 x20", "工具附魔会在工具升级后保留", "Bottomless", "Auto-Hook");
        assertThat(infinity.getIntent()).isEqualTo("guide");
        assertThat(infinity.getAnswer()).contains("银河之魂 x3", "火山晶石 x60", "无限之刃", "保留已有附魔");
        assertThat(rings.getIntent()).isEqualTo("guide");
        assertThat(rings.getAnswer()).contains("两个不同戒指", "火山晶石 x20", "不能把两个相同戒指", "铱环 + 幸运戒指");
        assertThat(forge.getSourceUrls()).contains("https://stardewvalleywiki.com/Forge");
        assertThat(legacyForge.getIntent()).isEqualTo("guide");
        assertThat(legacyForge.getAnswer()).doesNotContain("工具升级总览");
    }

    @Test
    void answersMasteryRewardsAndPriorityGuideLocally() {
        StardewGuideResult priority = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 精通先选哪个");
        StardewGuideResult rod = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 高级铱金鱼竿怎么获得");
        StardewGuideResult bait = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 挑战鱼饵怎么做");
        StardewGuideResult trinkets = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 铁砧和小饰品怎么用");

        assertThat(priority.getIntent()).isEqualTo("guide");
        assertThat(priority.getAnswer()).contains("10,000 / 15,000 / 20,000 / 25,000 / 30,000", "合计 100,000");
        assertThat(priority.getAnswer()).contains("耕种精通", "采矿精通", "觅食精通", "钓鱼精通", "战斗精通");
        assertThat(priority.getAnswer()).contains("铱金镰刀", "祝福雕像", "金色动物饼干", "矮人之王雕像", "重型熔炉");
        assertThat(rod.getAnswer()).contains("高级铱金鱼竿", "钓鱼精通", "25,000g", "2 个钓具");
        assertThat(bait.getAnswer()).contains("挑战鱼饵", "骨头碎片 x5", "苔藓 x2", "一次制作 5 个");
        assertThat(trinkets.getAnswer()).contains("小饰品与铁砧重铸", "铱锭 x3", "仙女盒", "寒冰法杖", "魔法箭筒");
        assertThat(priority.getSourceUrls()).contains("https://stardewvalleywiki.com/Mastery");
    }

    @Test
    void answersCombatTrinketsAndAnvilGuideLocally() {
        StardewGuideResult best = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 小饰品哪个好");
        StardewGuideResult frog = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 青蛙蛋适合刷怪物掉落吗");
        StardewGuideResult quiver = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 魔法箭筒铁砧刷什么词条");
        StardewGuideResult parrot = service.answerEvidence(StardewGuideIntent.GUIDE, "星露谷 鹦鹉蛋最高等级要多少钱");

        assertThat(best.getIntent()).isEqualTo("guide");
        assertThat(best.getAnswer())
                .contains("小饰品与铁砧重铸", "战斗精通", "只能装备 1 个小饰品")
                .contains("窃贼戒指和怪物图鉴不会提高小饰品掉落概率")
                .contains("铁锭 x50", "铱锭 x3", "10 分钟")
                .contains("仙女盒", "寒冰法杖", "魔法箭筒", "鹦鹉蛋", "蜥怪的爪子", "魔法发胶");
        assertThat(frog.getAnswer()).contains("被青蛙吃掉的敌人不会掉落物品", "不计入任务或怪物根除目标");
        assertThat(quiver.getAnswer()).contains("完美为 0.9 秒冷却", "30-35 伤害", "生成率 4%");
        assertThat(parrot.getAnswer()).contains("4 级需要总收入至少 2,250,000g", "刷钱");
        assertThat(best.getSourceUrls()).contains("https://stardewvalleywiki.com/Trinkets", "https://stardewvalleywiki.com/Anvil");
    }

    @Test
    void answersFishPondDetailsAndRecommendationsWithoutBuildingCrossRouting() {
        StardewGuideResult sturgeon = service.answerEvidence(StardewGuideIntent.FISH_POND, "星露谷 鲟鱼鱼塘产什么");
        StardewGuideResult lavaEel = service.answerEvidence(StardewGuideIntent.FISH_POND, "星露谷 岩浆鳗鱼鱼塘要什么");
        StardewGuideResult recommendations = service.answerEvidence(StardewGuideIntent.FISH_POND, "星露谷 鱼塘养什么好");
        StardewGuideResult building = service.answerEvidence(StardewGuideIntent.BUILDING, "星露谷 鱼塘建造材料多少钱");

        assertThat(sturgeon.getIntent()).isEqualTo("fish_pond_detail");
        assertThat(sturgeon.getAnswer()).contains("鲟鱼鱼塘", "初始 1", "上限 10", "1-2 鱼籽", "鱼籽酱");
        assertThat(sturgeon.getSourceUrls()).contains("https://stardewvalleywiki.com/Fish_Pond");

        assertThat(lavaEel.getIntent()).isEqualTo("fish_pond_detail");
        assertThat(lavaEel.getAnswer()).contains("岩浆鳗鱼鱼塘", "扩容任务", "3 火水晶", "1 铱锭", "5 香辣鳗鱼", "岩浆晶球");

        assertThat(recommendations.getIntent()).isEqualTo("fish_pond_available");
        assertThat(recommendations.getAnswer()).contains("鲟鱼", "岩浆鳗鱼", "冰柱鱼", "水滴鱼", "黄貂鱼", "午夜鱿鱼");
        assertThat(recommendations.getAnswer()).doesNotContain("花费：", "罗宾");

        assertThat(building.getIntent()).isEqualTo("building_detail");
        assertThat(building.getAnswer()).contains("鱼塘", "花费：5,000g", "石头 x200", "海草 x5", "绿藻 x5");
        assertThat(building.getAnswer()).doesNotContain("鱼塘产物与推荐", "扩容任务");
    }

    @Test
    void explicitUnknownEvidenceDoesNotFallBackToLegacyFreeTextRoute() {
        StardewGuideResult result = service.answerEvidence(StardewGuideIntent.UNKNOWN, "星露谷 鸡舍升级材料");

        assertThat(result.getIntent()).isEqualTo("unknown");
        assertThat(result.getAnswer()).isBlank();
    }

    @Test
    void answersCookingRecipeDetails() {
        StardewGuideResult luckyLunch = service.answer("星露谷 幸运午餐怎么做");
        StardewGuideResult spicyEel = service.answer("星露谷 香辣鳗鱼材料和效果");
        StardewGuideResult chocolateCake = service.answer("星露谷 巧克力蛋糕怎么做");
        StardewGuideResult sashimi = service.answer("星露谷 生鱼片材料");
        StardewGuideResult troutSoup = service.answer("星露谷 鳟鱼汤效果");

        assertThat(luckyLunch.getIntent()).isEqualTo("cooking_recipe");
        assertThat(luckyLunch.getAnswer()).contains("海参 x1", "玉米饼 x1", "蓝爵 x1", "运气 +3");
        assertThat(spicyEel.getIntent()).isEqualTo("cooking_recipe");
        assertThat(spicyEel.getAnswer()).contains("鳗鱼 x1", "辣椒 x1", "速度 +1", "运气 +1", "红宝石兑换");
        assertThat(chocolateCake.getIntent()).isEqualTo("cooking_recipe");
        assertThat(chocolateCake.getAnswer()).contains("小麦粉 x1", "糖 x1", "鸡蛋 x1");
        assertThat(sashimi.getIntent()).isEqualTo("cooking_recipe");
        assertThat(sashimi.getAnswer()).contains("任意鱼 x1", "莱纳斯 3 心邮件");
        assertThat(troutSoup.getIntent()).isEqualTo("cooking_recipe");
        assertThat(troutSoup.getAnswer()).contains("虹鳟鱼 x1", "绿藻 x1", "钓鱼 +1");
    }

    @Test
    void typedCookingEvidenceAnswersExpandedRecipeDetails() {
        StardewGuideResult pinkCake = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 粉红蛋糕怎么做");
        StardewGuideResult redPlate = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 红之盛宴效果");
        StardewGuideResult autumnsBounty = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 秋日恩赐材料和效果");
        StardewGuideResult chowder = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 海鲜杂烩汤材料和效果");
        StardewGuideResult bananaPudding = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 香蕉布丁怎么做");
        StardewGuideResult squidInkRavioli = service.answerEvidence(StardewGuideIntent.COOKING, "星露谷 墨汁意大利饺效果");

        assertThat(pinkCake.getIntent()).isEqualTo("cooking_recipe");
        assertThat(pinkCake.getAnswer()).contains("甜瓜 x1", "小麦粉 x1", "糖 x1", "鸡蛋 x1");
        assertThat(redPlate.getIntent()).isEqualTo("cooking_recipe");
        assertThat(redPlate.getAnswer()).contains("红叶卷心菜 x1", "萝卜 x1", "最大体力 +50");
        assertThat(autumnsBounty.getIntent()).isEqualTo("cooking_recipe");
        assertThat(autumnsBounty.getAnswer()).contains("山药 x1", "南瓜 x1", "觅食 +2", "防御 +2");
        assertThat(chowder.getIntent()).isEqualTo("cooking_recipe");
        assertThat(chowder.getAnswer()).contains("蛤 x1", "牛奶 x1", "钓鱼 +1", "约 16 分钟 47 秒");
        assertThat(bananaPudding.getIntent()).isEqualTo("cooking_recipe");
        assertThat(bananaPudding.getAnswer()).contains("香蕉 x1", "任意牛奶 x1", "骨头碎片 x30", "采矿 +1", "运气 +1", "防御 +1");
        assertThat(squidInkRavioli.getIntent()).isEqualTo("cooking_recipe");
        assertThat(squidInkRavioli.getAnswer()).contains("鱿鱼墨汁 x1", "西红柿 x1", "免疫负面效果 +1");
    }

    @Test
    void answersCookingBuffRecommendationLists() {
        StardewGuideResult fishing = service.answer("星露谷 钓鱼料理有哪些");
        StardewGuideResult combat = service.answer("星露谷 战斗等级低吃什么食物");
        StardewGuideResult earlyHealing = service.answer("星露谷 前期回血普通料理有哪些");
        StardewGuideResult island = service.answer("星露谷 姜岛料理有哪些");

        assertThat(fishing.getIntent()).isEqualTo("cooking_available");
        assertThat(fishing.getAnswer()).contains("海泡布丁", "海之菜肴", "龙虾浓汤", "鱼肉卷", "钓鱼 +");
        assertThat(combat.getIntent()).isEqualTo("cooking_available");
        assertThat(combat.getAnswer()).contains("块茎拼盘", "攻击 +3");
        assertThat(earlyHealing.getIntent()).isEqualTo("cooking_available");
        assertThat(earlyHealing.getAnswer()).contains("煎蛋", "沙拉", "烤鱼", "恢复体力和生命");
        assertThat(earlyHealing.getAnswer()).doesNotContain("夏季鱼类", "鸡舍升级", "工具升级");
        assertThat(island.getIntent()).isEqualTo("cooking_available");
        assertThat(island.getAnswer()).contains("香蕉布丁", "芒果糯米饭", "夏威夷芋泥", "热带咖喱");
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
