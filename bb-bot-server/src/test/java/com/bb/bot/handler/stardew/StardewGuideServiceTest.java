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
        assertThat(result.getAnswer()).contains("官方 Wiki", "姜岛", "金核桃");
        assertThat(result.getSourceUrls()).contains("https://zh.stardewvalleywiki.com/姜岛");
    }

    @Test
    void answersCombatLevelingLocally() {
        StardewGuideResult result = service.answer("星露谷 战斗技能如何快速升级");

        assertThat(result.getIntent()).isEqualTo("guide");
        assertThat(result.getAnswer()).contains("战斗经验来自击杀怪物");
        assertThat(result.getAnswer()).contains("矿井", "骷髅洞穴", "战士 -> 野蛮人");
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
