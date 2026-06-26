package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StardewKnowledgeRepositoryTest {

    private StardewKnowledgeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new StardewKnowledgeRepository();
        repository.load();
    }

    @Test
    void loadsBroadLocalKnowledgeBase() {
        assertThat(repository.gameVersion()).isEqualTo("1.6.15");
        assertThat(repository.fish()).hasSizeGreaterThanOrEqualTo(74);
        assertThat(repository.bundles()).hasSizeGreaterThanOrEqualTo(58);
        assertThat(repository.crops()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(repository.buildings()).hasSizeGreaterThanOrEqualTo(27);
        assertThat(repository.tools()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(repository.craftingRecipes()).hasSizeGreaterThanOrEqualTo(150);
        assertThat(repository.machines()).hasSizeGreaterThanOrEqualTo(80);
        assertThat(repository.shops()).hasSizeGreaterThanOrEqualTo(29);
        assertThat(repository.villagers()).hasSizeGreaterThanOrEqualTo(34);
        assertThat(repository.resources()).hasSizeGreaterThanOrEqualTo(181);
        assertThat(repository.monsterDrops()).hasSizeGreaterThanOrEqualTo(58);
        assertThat(repository.fishPonds()).hasSize(73);
        assertThat(repository.cookingRecipes()).hasSizeGreaterThanOrEqualTo(83);
        assertThat(repository.books()).hasSizeGreaterThanOrEqualTo(26);
        assertThat(repository.storyQuests()).hasSize(55);
        assertThat(repository.specialOrders()).hasSizeGreaterThanOrEqualTo(28);
        assertThat(repository.skillGuides()).hasSizeGreaterThanOrEqualTo(9);
        assertThat(repository.festivalEvents()).hasSize(12);
        assertThat(repository.farmMaps()).hasSize(8);
        assertThat(repository.farmAnimals()).hasSize(11);
        assertThat(repository.dungeonGuides()).hasSize(6);
        assertThat(repository.guides()).hasSizeGreaterThanOrEqualTo(39);
    }

    @Test
    void fullFarmAnimalTableIsCovered() {
        Set<String> animalIds = repository.farmAnimals().stream()
                .map(StardewData.FarmAnimalGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.farmAnimals()).hasSize(11);
        assertThat(animalIds).containsExactlyInAnyOrder(
                "chicken",
                "void_chicken",
                "golden_chicken",
                "duck",
                "rabbit",
                "dinosaur",
                "cow",
                "goat",
                "sheep",
                "pig",
                "ostrich"
        );

        assertThat(repository.findFarmAnimal("兔子的脚怎么出").orElseThrow().getId()).isEqualTo("rabbit");
        assertThat(repository.findFarmAnimal("鸭毛概率").orElseThrow().getId()).isEqualTo("duck");
        assertThat(repository.findFarmAnimal("大壶牛奶为什么不出").orElseThrow().getId()).isEqualTo("cow");
        assertThat(repository.findFarmAnimal("猪松露赚钱").orElseThrow().getId()).isEqualTo("pig");
        assertThat(repository.findFarmAnimal("鸵鸟蛋怎么孵").orElseThrow().getId()).isEqualTo("ostrich");
        assertThat(repository.findFarmAnimal("金鸡怎么获得").orElseThrow().getId()).isEqualTo("golden_chicken");
    }

    @Test
    void allFarmAnimalsHaveCoreFieldsAndSources() {
        assertThat(repository.farmAnimals())
                .hasSize(11)
                .allSatisfy(animal -> {
                    assertThat(animal.getId()).isNotBlank();
                    assertThat(animal.getName()).isNotBlank();
                    assertThat(animal.getNameEn()).isNotBlank();
                    assertThat(animal.getAliases()).isNotEmpty();
                    assertThat(animal.getCategory()).isIn("coop", "barn");
                    assertThat(animal.getBuilding()).isNotBlank();
                    assertThat(animal.getAcquisition()).isNotBlank();
                    assertThat(animal.getMaturityDays()).isNotNull();
                    assertThat(animal.getProduceFrequency()).isNotBlank();
                    assertThat(animal.getToolRequired()).isNotBlank();
                    assertThat(animal.getProducts()).isNotEmpty();
                    assertThat(animal.getBestFor()).isNotEmpty();
                    assertThat(animal.getRecommendations()).isNotEmpty();
                    assertThat(animal.getSourceUrls()).contains("https://stardewvalleywiki.com/Animals");
                });
    }

    @Test
    void fullDungeonGuideSetIsCoveredAsTypedCategory() {
        Set<String> dungeonIds = repository.dungeonGuides().stream()
                .map(StardewData.DungeonGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.dungeonGuides()).hasSize(6);
        assertThat(dungeonIds).containsExactlyInAnyOrder(
                "the_mines",
                "skull_cavern",
                "volcano_dungeon",
                "quarry_mine",
                "mutant_bug_lair",
                "witchs_swamp"
        );

        assertThat(repository.findDungeonGuide("矿井多少层").orElseThrow().getId()).isEqualTo("the_mines");
        assertThat(repository.findDungeonGuide("骷髅洞穴100层怎么冲").orElseThrow().getId()).isEqualTo("skull_cavern");
        assertThat(repository.findDungeonGuide("火山地牢怎么过").orElseThrow().getId()).isEqualTo("volcano_dungeon");
        assertThat(repository.findDungeonGuide("金镰刀在哪拿").orElseThrow().getId()).isEqualTo("quarry_mine");
        assertThat(repository.findDungeonGuide("突变虫穴史莱姆鱼").orElseThrow().getId()).isEqualTo("mutant_bug_lair");
        assertThat(repository.findDungeonGuide("女巫沼泽魔法墨水").orElseThrow().getId()).isEqualTo("witchs_swamp");
    }

    @Test
    void allDungeonGuidesHaveCoreFieldsAndSources() {
        assertThat(repository.dungeonGuides())
                .hasSize(6)
                .allSatisfy(dungeon -> {
                    assertThat(dungeon.getId()).isNotBlank();
                    assertThat(dungeon.getName()).isNotBlank();
                    assertThat(dungeon.getNameEn()).isNotBlank();
                    assertThat(dungeon.getAliases()).isNotEmpty();
                    assertThat(dungeon.getLocation()).isNotBlank();
                    assertThat(dungeon.getUnlock()).isNotBlank();
                    assertThat(dungeon.getFloorSummary()).isNotBlank();
                    assertThat(dungeon.getMechanics()).isNotEmpty();
                    assertThat(dungeon.getLoot()).isNotEmpty();
                    assertThat(dungeon.getQuests()).isNotEmpty();
                    assertThat(dungeon.getRecommendations()).isNotEmpty();
                    assertThat(dungeon.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void fullStoryQuestTableIsCoveredAsTypedCategory() {
        Set<String> questIds = repository.storyQuests().stream()
                .map(StardewData.StoryQuestGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.storyQuests()).hasSize(55);
        assertThat(questIds).contains(
                "quest_introductions",
                "quest_getting_started_crop",
                "quest_getting_started_meadowlands",
                "quest_robins_lost_axe",
                "quest_mayors_shorts",
                "quest_blackberry_basket",
                "quest_the_mysterious_qi",
                "quest_a_winter_mystery",
                "quest_strange_note",
                "quest_cryptic_note",
                "quest_dark_talisman",
                "quest_goblin_problem",
                "quest_the_pirates_wife",
                "quest_the_giant_stump"
        );

        assertThat(repository.findStoryQuest("罗宾斧头在哪").orElseThrow().getId()).isEqualTo("quest_robins_lost_axe");
        assertThat(repository.findStoryQuest("刘易斯短裤怎么拿").orElseThrow().getId()).isEqualTo("quest_mayors_shorts");
        assertThat(repository.findStoryQuest("黑莓篮子在哪").orElseThrow().getId()).isEqualTo("quest_blackberry_basket");
        assertThat(repository.findStoryQuest("神秘齐怎么做").orElseThrow().getId()).isEqualTo("quest_the_mysterious_qi");
        assertThat(repository.findStoryQuest("骷髅洞穴100层秘密纸条").orElseThrow().getId()).isEqualTo("quest_cryptic_note");
        assertThat(repository.findStoryQuest("虚空蛋黄酱哥布林").orElseThrow().getId()).isEqualTo("quest_goblin_problem");
        assertThat(repository.findStoryQuest("海盗妻子任务流程").orElseThrow().getId()).isEqualTo("quest_the_pirates_wife");
    }

    @Test
    void allStoryQuestsHaveCoreFieldsAndSources() {
        assertThat(repository.storyQuests())
                .hasSize(55)
                .allSatisfy(quest -> {
                    assertThat(quest.getId()).isNotBlank();
                    assertThat(quest.getName()).isNotBlank();
                    assertThat(quest.getNameEn()).isNotBlank();
                    assertThat(quest.getAliases()).isNotEmpty();
                    assertThat(quest.getTrigger()).isNotBlank();
                    assertThat(quest.getRequirements()).isNotEmpty();
                    assertThat(quest.getRewards()).isNotEmpty();
                    assertThat(quest.getRecommendation()).isNotBlank();
                    assertThat(quest.getSourceUrls()).contains("https://stardewvalleywiki.com/Quests");
                });
    }

    @Test
    void fullFarmMapTableIsCovered() {
        Set<String> mapIds = repository.farmMaps().stream()
                .map(StardewData.FarmMapGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.farmMaps()).hasSize(8);
        assertThat(mapIds).containsExactlyInAnyOrder(
                "standard_farm",
                "riverland_farm",
                "forest_farm",
                "hill_top_farm",
                "wilderness_farm",
                "four_corners_farm",
                "beach_farm",
                "meadowlands_farm"
        );

        assertThat(repository.findFarmMap("海滩农场洒水器").orElseThrow().getId()).isEqualTo("beach_farm");
        assertThat(repository.findFarmMap("草原农场开局鸡").orElseThrow().getId()).isEqualTo("meadowlands_farm");
        assertThat(repository.findFarmMap("四角农场多人").orElseThrow().getId()).isEqualTo("four_corners_farm");
        assertThat(repository.findFarmMap("森林农场硬木").orElseThrow().getId()).isEqualTo("forest_farm");
        assertThat(repository.findFarmMap("河流农场鱼熏机").orElseThrow().getId()).isEqualTo("riverland_farm");
    }

    @Test
    void allFarmMapsHaveCoreFieldsAndSources() {
        assertThat(repository.farmMaps())
                .hasSize(8)
                .allSatisfy(map -> {
                    assertThat(map.getId()).isNotBlank();
                    assertThat(map.getName()).isNotBlank();
                    assertThat(map.getNameEn()).isNotBlank();
                    assertThat(map.getAliases()).isNotEmpty();
                    assertThat(map.getAssociatedSkills()).isNotEmpty();
                    assertThat(map.getTillableTiles()).isNotNull();
                    assertThat(map.getLayoutSummary()).isNotBlank();
                    assertThat(map.getPerks()).isNotEmpty();
                    assertThat(map.getRecommendations()).isNotEmpty();
                    assertThat(map.getBestFor()).isNotEmpty();
                    assertThat(map.getSourceUrls()).contains("https://stardewvalleywiki.com/Farm_Maps");
                });
    }

    @Test
    void fullFestivalCalendarIsCovered() {
        Set<String> eventIds = repository.festivalEvents().stream()
                .map(StardewData.FestivalEvent::getId)
                .collect(Collectors.toSet());

        assertThat(repository.festivalEvents()).hasSize(12);
        assertThat(eventIds).containsExactlyInAnyOrder(
                "egg_festival",
                "desert_festival",
                "flower_dance",
                "luau",
                "trout_derby",
                "dance_of_the_moonlight_jellies",
                "stardew_valley_fair",
                "spirits_eve",
                "festival_of_ice",
                "squidfest",
                "night_market",
                "feast_of_the_winter_star"
        );

        assertThat(repository.findFestivalEvent("沙漠节怎么玩").orElseThrow().getId()).isEqualTo("desert_festival");
        assertThat(repository.findFestivalEvent("花舞节几点开始").orElseThrow().getId()).isEqualTo("flower_dance");
        assertThat(repository.findFestivalEvent("展览会怎么拿星之果实").orElseThrow().getId()).isEqualTo("stardew_valley_fair");
        assertThat(repository.findFestivalEvent("冬星盛宴送什么").orElseThrow().getId()).isEqualTo("feast_of_the_winter_star");
    }

    @Test
    void allFestivalEventsHaveCoreFieldsAndSources() {
        assertThat(repository.festivalEvents())
                .hasSize(12)
                .allSatisfy(event -> {
                    assertThat(event.getId()).isNotBlank();
                    assertThat(event.getName()).isNotBlank();
                    assertThat(event.getNameEn()).isNotBlank();
                    assertThat(event.getAliases()).isNotEmpty();
                    assertThat(event.getSeason()).isNotBlank();
                    assertThat(event.getStartDay()).isNotNull();
                    assertThat(event.getEndDay()).isNotNull();
                    assertThat(event.getLocation()).isNotBlank();
                    assertThat(event.getEntryTime()).isNotBlank();
                    assertThat(event.getTimePasses()).isNotNull();
                    assertThat(event.getShopsClosed()).isNotNull();
                    assertThat(event.getAnimalsNeedFeeding()).isNotNull();
                    assertThat(event.getActivities()).isNotEmpty();
                    assertThat(event.getRewards()).isNotEmpty();
                    assertThat(event.getRecommendation()).isNotBlank();
                    assertThat(event.getSourceUrls()).contains("https://stardewvalleywiki.com/Festivals");
                });
    }

    @Test
    void fullFishPondProductTableIsCovered() {
        Set<String> pondIds = repository.fishPonds().stream()
                .map(StardewData.FishPondGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.fishPonds()).hasSize(73);
        assertThat(pondIds).contains(
                "fish_pond_sturgeon",
                "fish_pond_lava_eel",
                "fish_pond_stingray",
                "fish_pond_blobfish",
                "fish_pond_legend",
                "fish_pond_coral",
                "fish_pond_sea_urchin"
        );

        StardewData.FishPondGuide sturgeon = repository.findFishPond("鲟鱼鱼塘产什么").orElseThrow();
        assertThat(sturgeon.getProducts()).extracting(StardewData.FishPondProduct::getItem)
                .contains("1-2 鱼籽");
        assertThat(sturgeon.getQuests()).extracting(StardewData.FishPondQuest::getItemsRequired)
                .contains("1 钻石", "1 鹦鹉螺壳");

        StardewData.FishPondGuide stingray = repository.findFishPond("黄貂鱼鱼塘产什么").orElseThrow();
        assertThat(stingray.getProducts()).extracting(StardewData.FishPondProduct::getItem)
                .contains("1 龙牙", "1 电池组");

        StardewData.FishPondGuide coral = repository.findFishPond("珊瑚鱼塘").orElseThrow();
        assertThat(coral.getInitialCapacity()).isEqualTo(10);
    }

    @Test
    void allFishPondGuidesHaveCoreFieldsAndSources() {
        assertThat(repository.fishPonds())
                .hasSize(73)
                .allSatisfy(fishPond -> {
                    assertThat(fishPond.getId()).isNotBlank();
                    assertThat(fishPond.getFishName()).isNotBlank();
                    assertThat(fishPond.getFishNameEn()).isNotBlank();
                    assertThat(fishPond.getAliases()).isNotEmpty();
                    assertThat(fishPond.getInitialCapacity()).isNotNull();
                    assertThat(fishPond.getMaxCapacity()).isNotNull();
                    assertThat(fishPond.getProducts()).isNotEmpty();
                    assertThat(fishPond.getRecommendation()).isNotBlank();
                    assertThat(fishPond.getSourceUrls()).contains("https://stardewvalleywiki.com/Fish_Pond");
                });
    }

    @Test
    void specialCurrenciesAreCoveredAsResourcesAndGuide() {
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(resourceIds).contains(
                "qi_gem",
                "golden_walnut",
                "qi_coin",
                "star_token",
                "prize_ticket",
                "calico_egg",
                "cinder_shard",
                "golden_tag"
        );
        assertThat(repository.findResource("星星币怎么刷").orElseThrow().getId()).isEqualTo("star_token");
        assertThat(repository.findResource("金色标签怎么获得").orElseThrow().getId()).isEqualTo("golden_tag");
        assertThat(repository.findResource("火山晶石怎么用").orElseThrow().getId()).isEqualTo("cinder_shard");

        StardewData.GuideTopic guide = repository.findGuide("特殊货币有哪些").orElseThrow();
        assertThat(guide.getId()).isEqualTo("special_currencies");
        assertThat(guide.getSections()).extracting(StardewData.GuideSection::getTitle)
                .contains("永久进度类", "赌场和节日类", "票券类");
    }

    @Test
    void allSpecialCurrencyResourcesHaveCoreFieldsAndSources() {
        List<String> ids = List.of(
                "qi_gem",
                "golden_walnut",
                "qi_coin",
                "star_token",
                "prize_ticket",
                "calico_egg",
                "cinder_shard",
                "golden_tag"
        );

        assertThat(repository.resources())
                .filteredOn(resource -> ids.contains(resource.getId()))
                .hasSize(ids.size())
                .allSatisfy(resource -> {
                    assertThat(resource.getName()).isNotBlank();
                    assertThat(resource.getAliases()).isNotEmpty();
                    assertThat(resource.getAcquisitions()).isNotEmpty();
                    assertThat(resource.getRecommendation()).isNotBlank();
                    assertThat(resource.getUsedIn()).isNotEmpty();
                    assertThat(resource.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void islandFieldOfficeAndIslandVolcanoResourcesAreCoveredAsFullSet() {
        List<String> ids = List.of(
                "golden_coconut",
                "fossilized_skull",
                "fossilized_spine",
                "fossilized_tail",
                "fossilized_leg",
                "fossilized_ribs",
                "snake_skull",
                "snake_vertebrae",
                "mummified_bat",
                "mummified_frog",
                "ginger",
                "magma_cap"
        );

        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(resourceIds).containsAll(ids);
        assertThat(repository.findResource("金色椰子怎么开").orElseThrow().getId()).isEqualTo("golden_coconut");
        assertThat(repository.findResource("化石脊柱哪里钓").orElseThrow().getId()).isEqualTo("fossilized_spine");
        assertThat(repository.findResource("蛇头骨怎么获得").orElseThrow().getId()).isEqualTo("snake_skull");
        assertThat(repository.findResource("蛇椎骨哪里刷").orElseThrow().getId()).isEqualTo("snake_vertebrae");
        assertThat(repository.findResource("木乃伊蝙蝠哪里刷").orElseThrow().getId()).isEqualTo("mummified_bat");
        assertThat(repository.findResource("木乃伊青蛙怎么拿").orElseThrow().getId()).isEqualTo("mummified_frog");
        assertThat(repository.findResource("生姜怎么获得").orElseThrow().getId()).isEqualTo("ginger");
        assertThat(repository.findResource("岩浆菇哪里找").orElseThrow().getId()).isEqualTo("magma_cap");

        assertThat(repository.resources())
                .filteredOn(resource -> ids.contains(resource.getId()))
                .hasSize(ids.size())
                .allSatisfy(resource -> {
                    assertThat(resource.getAliases()).isNotEmpty();
                    assertThat(resource.getAcquisitions()).isNotEmpty();
                    assertThat(resource.getRecommendation()).isNotBlank();
                    assertThat(resource.getUsedIn()).isNotEmpty();
                    assertThat(resource.getSourceUrls()).isNotEmpty();
                });

        StardewData.GuideTopic guide = repository.findGuide("岛屿办事处化石怎么捐").orElseThrow();
        assertThat(guide.getId()).isEqualTo("island_field_office");
        assertThat(guide.getSections()).extracting(StardewData.GuideSection::getTitle)
                .contains("大型动物化石", "蛇化石", "蝙蝠和青蛙", "调查答案");
    }

    @Test
    void keepsCoreGuideCategoriesCovered() {
        Set<String> categories = repository.guides().stream()
                .map(StardewData.GuideTopic::getCategory)
                .collect(Collectors.toSet());

        assertThat(categories).contains(
                "tools",
                "buildings",
                "crops",
                "skills",
                "villagers",
                "progression",
                "collection",
                "mining",
                "crafting",
                "animals",
                "combat"
        );
    }

    @Test
    void skillGuidesAreCoveredAsCompleteTypedCategory() {
        Set<String> ids = repository.skillGuides().stream()
                .map(StardewData.SkillGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.skillGuides()).hasSize(9);
        assertThat(ids).containsExactlyInAnyOrder(
                "farming_skill",
                "mining_skill",
                "foraging_skill",
                "fishing_skill",
                "combat_skill",
                "profession_reset",
                "skill_books",
                "skill_food_buffs",
                "mastery"
        );

        assertThat(repository.findSkillGuide("战斗等级低怎么快速升级").orElseThrow().getId()).isEqualTo("combat_skill");
        assertThat(repository.findSkillGuide("钓鱼职业怎么选").orElseThrow().getId()).isEqualTo("fishing_skill");
        assertThat(repository.findSkillGuide("技能书怎么买").orElseThrow().getId()).isEqualTo("skill_books");
        assertThat(repository.findSkillGuide("职业怎么重置").orElseThrow().getId()).isEqualTo("profession_reset");
        assertThat(repository.findSkillGuide("精通先选哪个").orElseThrow().getId()).isEqualTo("mastery");
    }

    @Test
    void allSkillGuidesHaveCoreFieldsAndSources() {
        assertThat(repository.skillGuides())
                .hasSize(9)
                .allSatisfy(guide -> {
                    assertThat(guide.getId()).isNotBlank();
                    assertThat(guide.getName()).isNotBlank();
                    assertThat(guide.getNameEn()).isNotBlank();
                    assertThat(guide.getAliases()).isNotEmpty();
                    assertThat(guide.getKeywords()).isNotEmpty();
                    assertThat(guide.getSections()).isNotEmpty();
                    assertThat(guide.getRecommendation()).isNotBlank();
                    assertThat(guide.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void specialOrdersAreCoveredAsFullTownAndQiBoards() {
        Set<String> ids = repository.specialOrders().stream()
                .map(StardewData.SpecialOrderGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.specialOrders()).hasSize(28);
        assertThat(ids).contains(
                "special_order_island_ingredients",
                "special_order_cave_patrol",
                "special_order_aquatic_overpopulation",
                "special_order_biome_balance",
                "special_order_rock_rejuvenation",
                "special_order_gifts_for_george",
                "special_order_fragments_of_the_past",
                "special_order_gus_famous_omelet",
                "special_order_crop_order",
                "special_order_community_cleanup",
                "special_order_the_strong_stuff",
                "special_order_pierres_prime_produce",
                "special_order_robins_project",
                "special_order_robins_resource_rush",
                "special_order_juicy_bugs_wanted",
                "special_order_tropical_fish",
                "special_order_a_curious_substance",
                "special_order_prismatic_jelly",
                "qi_order_qis_crop",
                "qi_order_lets_play_a_game",
                "qi_order_four_precious_stones",
                "qi_order_hungry_challenge",
                "qi_order_qis_cuisine",
                "qi_order_qis_kindness",
                "qi_order_extended_family",
                "qi_order_danger_in_the_deep",
                "qi_order_skull_cavern_invasion",
                "qi_order_prismatic_grange"
        );
        assertThat(repository.specialOrders())
                .filteredOn(order -> "鹈鹕镇特别订单板".equals(order.getBoard()))
                .hasSize(18);
        assertThat(repository.specialOrders())
                .filteredOn(order -> "齐先生核桃房特别订单板".equals(order.getBoard()))
                .hasSize(10);

        assertThat(repository.findSpecialOrder("罗宾资源冲刺奖励是什么").orElseThrow().getId())
                .isEqualTo("special_order_robins_resource_rush");
        assertThat(repository.findSpecialOrder("岛屿食材要什么").orElseThrow().getId())
                .isEqualTo("special_order_island_ingredients");
        assertThat(repository.findSpecialOrder("齐瓜怎么做").orElseThrow().getId())
                .isEqualTo("qi_order_qis_crop");
        assertThat(repository.findSpecialOrder("五彩农场交什么").orElseThrow().getId())
                .isEqualTo("qi_order_prismatic_grange");
    }

    @Test
    void allSpecialOrdersHaveCoreFieldsAndSources() {
        assertThat(repository.specialOrders())
                .allSatisfy(order -> {
                    assertThat(order.getId()).isNotBlank();
                    assertThat(order.getName()).isNotBlank();
                    assertThat(order.getAliases()).isNotEmpty();
                    assertThat(order.getBoard()).isNotBlank();
                    assertThat(order.getTimeframe()).isNotBlank();
                    assertThat(order.getRequirements()).isNotEmpty();
                    assertThat(order.getRewards()).isNotEmpty();
                    assertThat(order.getRecommendation()).isNotBlank();
                    assertThat(order.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void forgeEnchantingGuideIsCovered() {
        StardewData.GuideTopic guide = repository.guides().stream()
                .filter(topic -> "forge_enchanting".equals(topic.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(guide.getName()).isEqualTo("火山锻造与附魔");
        assertThat(guide.getAliases()).contains("火山锻造", "工具附魔", "银河之魂", "戒指合成");
        assertThat(guide.getSections()).extracting(StardewData.GuideSection::getTitle)
                .contains("武器宝石锻造", "武器和工具附魔", "无限武器", "戒指合成和拆解");
        assertThat(guide.getSourceUrls()).contains("https://stardewvalleywiki.com/Forge");
    }

    @Test
    void combatTrinketsGuideIsCovered() {
        StardewData.GuideTopic guide = repository.guides().stream()
                .filter(topic -> "combat_trinkets".equals(topic.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(guide.getName()).isEqualTo("小饰品与铁砧重铸");
        assertThat(guide.getCategory()).isEqualTo("combat");
        assertThat(guide.getAliases()).contains("小饰品", "铁砧重铸", "青蛙蛋", "魔法箭筒", "鹦鹉蛋");
        assertThat(guide.getSections()).extracting(StardewData.GuideSection::getTitle)
                .contains("解锁和获取", "掉落机制和刷法", "铁砧重铸", "全部小饰品效果", "选择建议");
        assertThat(guide.getSourceUrls()).contains("https://stardewvalleywiki.com/Trinkets", "https://stardewvalleywiki.com/Anvil");
    }

    @Test
    void allGuidesHaveContentAndSources() {
        assertThat(repository.guides())
                .allSatisfy(guide -> {
                    assertThat(guide.getId()).isNotBlank();
                    assertThat(guide.getName()).isNotBlank();
                    assertThat(guide.getSections()).isNotEmpty();
                    assertThat(guide.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void normalCommunityCenterBundlesAreCovered() {
        Set<String> bundleIds = repository.bundles().stream()
                .map(StardewData.Bundle::getId)
                .collect(Collectors.toSet());

        assertThat(bundleIds).contains(
                "spring_foraging",
                "summer_foraging",
                "fall_foraging",
                "winter_foraging",
                "construction",
                "exotic_foraging",
                "spring_crops",
                "summer_crops",
                "fall_crops",
                "quality_crops",
                "animal",
                "artisan",
                "river_fish",
                "lake_fish",
                "ocean_fish",
                "night_fishing",
                "crab_pot",
                "specialty_fish",
                "blacksmith",
                "geologist",
                "adventurer",
                "chef",
                "dye",
                "field_research",
                "fodder",
                "enchanter",
                "vault_2500",
                "vault_5000",
                "vault_10000",
                "vault_25000",
                "crafts_room",
                "pantry_room",
                "fish_tank_room",
                "bulletin_board_room",
                "vault_room",
                "missing"
        );
    }

    @Test
    void remixedCommunityCenterBundlesAreCovered() {
        Set<String> bundleIds = repository.bundles().stream()
                .map(StardewData.Bundle::getId)
                .collect(Collectors.toSet());

        assertThat(bundleIds).contains(
                "remixed_sticky",
                "remixed_forest",
                "remixed_wild_medicine",
                "remixed_spring_crops",
                "remixed_summer_crops",
                "remixed_fall_crops",
                "remixed_rare_crops",
                "remixed_fish_farmer",
                "remixed_garden",
                "remixed_brewers",
                "remixed_quality_fish",
                "remixed_master_fisher",
                "remixed_adventurer",
                "remixed_treasure_hunter",
                "remixed_engineer",
                "remixed_children",
                "remixed_forager",
                "remixed_home_cook",
                "remixed_helper",
                "remixed_spirits_eve",
                "remixed_winter_star"
        );
    }

    @Test
    void allBundlesHaveItemsRewardsAndSources() {
        assertThat(repository.bundles())
                .allSatisfy(bundle -> {
                    assertThat(bundle.getId()).isNotBlank();
                    assertThat(bundle.getName()).isNotBlank();
                    assertThat(bundle.getRoom()).isNotBlank();
                    assertThat(bundle.getReward()).isNotBlank();
                    assertThat(bundle.getItems()).isNotEmpty();
                    assertThat(bundle.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void villagerProfilesIncludeGiftData() {
        assertThat(repository.villagers())
                .allSatisfy(villager -> {
                    assertThat(villager.getBirthday()).isNotBlank();
                    assertThat(villager.getLovedGifts()).isNotEmpty();
                    assertThat(villager.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void allVillagersHaveStructuredSchedules() {
        assertThat(repository.villagers())
                .hasSizeGreaterThanOrEqualTo(34)
                .allSatisfy(villager -> {
                    assertThat(villager.getSchedules()).isNotEmpty();
                    assertThat(villager.getSchedules())
                            .allSatisfy(schedule -> {
                                assertThat(schedule.getLabel()).isNotBlank();
                                assertThat(schedule.getEvents()).isNotEmpty();
                                assertThat(schedule.getEvents())
                                        .allSatisfy(event -> {
                                            assertThat(event.getTime()).isNotBlank();
                                            assertThat(event.getLocation()).isNotBlank();
                                        });
                            });
                });
    }

    @Test
    void allCropsHaveGrowthAndSourceData() {
        assertThat(repository.crops())
                .allSatisfy(crop -> {
                    assertThat(crop.getId()).isNotBlank();
                    assertThat(crop.getName()).isNotBlank();
                    assertThat(crop.getSeasons()).isNotEmpty();
                    assertThat(crop.getGrowDays()).isPositive();
                    assertThat(crop.getSellPrice()).isPositive();
                    assertThat(crop.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void allBuildingsHaveCostMaterialsAndSources() {
        assertThat(repository.buildings())
                .allSatisfy(building -> {
                    assertThat(building.getId()).isNotBlank();
                    assertThat(building.getName()).isNotBlank();
                    assertThat(building.getCost()).isNotNull();
                    assertThat(building.getRecommendation()).isNotBlank();
                    assertThat(building.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void lateGameMagicAndCommunityBuildingsAreCovered() {
        Set<String> buildingIds = repository.buildings().stream()
                .map(StardewData.Building::getId)
                .collect(Collectors.toSet());

        assertThat(buildingIds).contains(
                "earth_obelisk",
                "water_obelisk",
                "desert_obelisk",
                "island_obelisk",
                "farm_obelisk",
                "junimo_hut",
                "gold_clock",
                "community_upgrade_pam_house",
                "town_shortcuts"
        );
    }

    @Test
    void allToolsHaveUpgradeDataAndSources() {
        assertThat(repository.tools())
                .allSatisfy(tool -> {
                    assertThat(tool.getId()).isNotBlank();
                    assertThat(tool.getName()).isNotBlank();
                    assertThat(tool.getUpgrades()).isNotEmpty();
                    assertThat(tool.getRecommendation()).isNotBlank();
                    assertThat(tool.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void commonMerchantsAndExchangeShopsAreCovered() {
        Set<String> shopIds = repository.shops().stream()
                .map(StardewData.Shop::getId)
                .collect(Collectors.toSet());

        assertThat(shopIds).contains(
                "pierres_general_store",
                "blacksmith",
                "carpenters_shop",
                "marnies_ranch",
                "willys_fish_shop",
                "krobus_shop",
                "traveling_cart",
                "desert_trader",
                "bookseller",
                "adventurers_guild",
                "dwarf_shop",
                "oasis",
                "island_trader",
                "qis_walnut_room",
                "stardrop_saloon",
                "harveys_clinic",
                "jojamart",
                "casino",
                "volcano_dwarf_shop",
                "wizards_tower",
                "ice_cream_stand",
                "abandoned_house",
                "giant_stump",
                "egg_festival_shop",
                "flower_dance_shop",
                "moonlight_jellies_shop",
                "stardew_valley_fair_shop",
                "spirits_eve_shop",
                "desert_festival_shop"
        );
    }

    @Test
    void allMachinesHaveRecipeUsageAndSources() {
        assertThat(repository.machines())
                .allSatisfy(machine -> {
                    assertThat(machine.getId()).isNotBlank();
                    assertThat(machine.getName()).isNotBlank();
                    assertThat(machine.getRecipeSource()).isNotBlank();
                    assertThat(machine.getMaterials()).isNotEmpty();
                    assertThat(machine.getOutputs()).isNotEmpty();
                    assertThat(machine.getRecommendation()).isNotBlank();
                    assertThat(machine.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void fullCraftingRecipeTableIsCoveredSeparatelyFromMachineCompatibilityData() {
        Set<String> recipeIds = repository.craftingRecipes().stream()
                .map(StardewData.CraftingRecipe::getId)
                .collect(Collectors.toSet());

        assertThat(repository.craftingRecipes()).hasSize(150);
        assertThat(recipeIds).contains(
                "gate",
                "wood_fence",
                "hardwood_fence",
                "cask",
                "tea_sapling",
                "fiber_seeds",
                "wood_floor",
                "torch",
                "tapper",
                "worm_bin",
                "flute_block",
                "stone_sign",
                "garden_pot",
                "explosive_ammo",
                "mini_forge"
        );
        assertThat(repository.machines()).hasSize(80);
    }

    @Test
    void allCraftingRecipesHaveCoreFieldsMaterialsAndSources() {
        assertThat(repository.craftingRecipes())
                .allSatisfy(recipe -> {
                    assertThat(recipe.getId()).isNotBlank();
                    assertThat(recipe.getName()).isNotBlank();
                    assertThat(recipe.getNameEn()).isNotBlank();
                    assertThat(recipe.getCategory()).isNotBlank();
                    assertThat(recipe.getRecipeSource()).isNotBlank();
                    assertThat(recipe.getMaterials()).isNotEmpty();
                    assertThat(recipe.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void findsCraftingRecipesAcrossOfficialCraftingCategories() {
        assertThat(repository.findCraftingRecipe("木栅栏怎么做").orElseThrow().getId())
                .isEqualTo("wood_fence");
        assertThat(repository.findCraftingRecipe("茶苗材料").orElseThrow().getId())
                .isEqualTo("tea_sapling");
        assertThat(repository.findCraftingRecipe("树液采集器配方").orElseThrow().getId())
                .isEqualTo("tapper");
        assertThat(repository.findCraftingRecipe("迷你锻造台怎么做").orElseThrow().getId())
                .isEqualTo("mini_forge");
        assertThat(repository.findCraftingRecipe("生命药水怎么做").orElseThrow().getId())
                .isEqualTo("life_elixir");
    }

    @Test
    void expandedCraftingDevicesAreCovered() {
        Set<String> machineIds = repository.machines().stream()
                .map(StardewData.Machine::getId)
                .collect(Collectors.toSet());

        assertThat(machineIds).contains(
                "sprinkler",
                "quality_sprinkler",
                "iridium_sprinkler",
                "cherry_bomb",
                "bomb",
                "mega_bomb",
                "staircase",
                "chest",
                "big_chest",
                "stone_chest",
                "big_stone_chest",
                "wood_sign",
                "scarecrow",
                "deluxe_scarecrow",
                "heavy_furnace",
                "solar_panel",
                "slime_incubator",
                "slime_egg_press",
                "bone_mill",
                "geode_crusher",
                "hopper",
                "farm_computer"
        );
    }

    @Test
    void expandedCraftingConsumablesFertilizersTotemsAndRingsAreCovered() {
        Set<String> machineIds = repository.machines().stream()
                .map(StardewData.Machine::getId)
                .collect(Collectors.toSet());

        assertThat(machineIds).contains(
                "basic_fertilizer",
                "quality_fertilizer",
                "deluxe_fertilizer",
                "speed_gro",
                "deluxe_speed_gro",
                "hyper_speed_gro",
                "basic_retaining_soil",
                "quality_retaining_soil",
                "deluxe_retaining_soil",
                "tree_fertilizer",
                "monster_musk",
                "fairy_dust",
                "warp_totem_beach",
                "warp_totem_mountains",
                "warp_totem_farm",
                "warp_totem_desert",
                "warp_totem_island",
                "rain_totem",
                "treasure_totem",
                "sturdy_ring",
                "warrior_ring",
                "ring_of_yoba",
                "thorns_ring",
                "glowstone_ring",
                "iridium_band",
                "wedding_ring"
        );
    }

    @Test
    void expandedFishingBaitTackleAndCrabPotCraftablesAreCovered() {
        Set<String> machineIds = repository.machines().stream()
                .map(StardewData.Machine::getId)
                .collect(Collectors.toSet());

        assertThat(machineIds).contains(
                "spinner",
                "trap_bobber",
                "sonar_bobber",
                "cork_bobber",
                "quality_bobber",
                "treasure_hunter",
                "dressed_spinner",
                "barbed_hook",
                "magnet",
                "bait",
                "deluxe_bait",
                "wild_bait",
                "magic_bait",
                "challenge_bait",
                "crab_pot_item"
        );
    }

    @Test
    void allReleaseShopsHaveStockAndSources() {
        assertThat(repository.shops())
                .allSatisfy(shop -> {
                    assertThat(shop.getId()).isNotBlank();
                    assertThat(shop.getName()).isNotBlank();
                    assertThat(shop.getLocation()).isNotBlank();
                    assertThat(shop.getOpenHours()).isNotBlank();
                    assertThat(shop.getStock()).isNotEmpty();
                    assertThat(shop.getRecommendation()).isNotBlank();
                    assertThat(shop.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void allCookingRecipesHaveIngredientsEffectsAndSources() {
        assertThat(repository.cookingRecipes())
                .allSatisfy(recipe -> {
                    assertThat(recipe.getId()).isNotBlank();
                    assertThat(recipe.getName()).isNotBlank();
                    assertThat(recipe.getRecipeSource()).isNotBlank();
                    assertThat(recipe.getEffect()).isNotBlank();
                    assertThat(recipe.getTags()).isNotEmpty();
                    assertThat(recipe.getRecommendation()).isNotBlank();
                    assertThat(recipe.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void commonCookingRecipesAreCovered() {
        Set<String> recipeIds = repository.cookingRecipes().stream()
                .map(StardewData.CookingRecipe::getId)
                .collect(Collectors.toSet());

        assertThat(recipeIds).contains(
                "fried_egg",
                "omelet",
                "salad",
                "cheese_cauliflower",
                "baked_fish",
                "parsnip_soup",
                "vegetable_medley",
                "complete_breakfast",
                "fried_calamari",
                "strange_bun",
                "fried_mushroom",
                "pizza",
                "bean_hotpot",
                "glazed_yams",
                "carp_surprise",
                "hashbrowns",
                "pancakes",
                "salmon_dinner",
                "fish_taco",
                "crispy_bass",
                "bread",
                "tom_kha_soup",
                "trout_soup",
                "chocolate_cake",
                "sashimi",
                "maki_roll",
                "tortilla",
                "pink_cake",
                "rhubarb_pie",
                "cookie",
                "spaghetti",
                "fried_eel",
                "red_plate",
                "rice_pudding",
                "ice_cream",
                "blueberry_tart",
                "autumns_bounty",
                "super_meal",
                "cranberry_sauce",
                "stuffing",
                "algae_soup",
                "pale_broth",
                "plum_pudding",
                "artichoke_dip",
                "stir_fry",
                "roasted_hazelnuts",
                "pumpkin_pie",
                "radish_salad",
                "fruit_salad",
                "blackberry_cobbler",
                "cranberry_candy",
                "bruschetta",
                "coleslaw",
                "fiddlehead_risotto",
                "poppyseed_muffin",
                "chowder",
                "escargot",
                "shrimp_cocktail",
                "banana_pudding",
                "mango_sticky_rice",
                "poi",
                "tropical_curry",
                "squid_ink_ravioli",
                "moss_soup"
        );
    }

    @Test
    void rareResourceGuidesAreCovered() {
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(resourceIds).contains(
                "dinosaur_egg",
                "dinosaur_mayonnaise",
                "dwarf_scrolls",
                "rabbit_foot",
                "caviar",
                "nautilus_shell",
                "red_cabbage",
                "fiddlehead_fern",
                "truffle",
                "duck_feather",
                "aquamarine",
                "void_salmon",
                "squid_ink",
                "ectoplasm",
                "radioactive_ore",
                "dragon_tooth"
        );
    }

    @Test
    void bookGuidesCoverSkillAndPowerBooks() {
        Set<String> bookIds = repository.books().stream()
                .map(StardewData.BookGuide::getId)
                .collect(Collectors.toSet());

        assertThat(bookIds).contains(
                "price_catalogue",
                "mapping_cave_systems",
                "way_of_the_wind_1",
                "way_of_the_wind_2",
                "monster_compendium",
                "friendship_101",
                "jack_be_nimble",
                "woodys_secret",
                "raccoon_journal",
                "jewels_of_the_sea",
                "dwarvish_safety_manual",
                "the_art_o_crabbing",
                "the_alleyway_buffet",
                "the_diamond_hunter",
                "book_of_mysteries",
                "horse_the_book",
                "treasure_appraisal_guide",
                "ol_slitherlegs",
                "animal_catalogue",
                "bait_and_bobber",
                "book_of_stars",
                "combat_quarterly",
                "mining_monthly",
                "stardew_valley_almanac",
                "woodcutters_weekly",
                "queen_of_sauce_cookbook"
        );
        assertThat(repository.books())
                .allSatisfy(book -> {
                    assertThat(book.getName()).isNotBlank();
                    assertThat(book.getEffect()).isNotBlank();
                    assertThat(book.getAcquisitions()).isNotEmpty();
                    assertThat(book.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void museumArtifactMineralAndGeodeGuidesAreCovered() {
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());
        Set<String> guideAliases = repository.guides().stream()
                .filter(guide -> "museum_donations".equals(guide.getId()))
                .flatMap(guide -> guide.getAliases().stream())
                .collect(Collectors.toSet());

        assertThat(resourceIds).contains(
                "artifact_spot",
                "artifact_trove",
                "geode",
                "frozen_geode",
                "magma_geode",
                "omni_geode",
                "quartz",
                "earth_crystal",
                "frozen_tear",
                "fire_quartz",
                "amethyst",
                "jade",
                "ruby",
                "diamond",
                "dwarf_gadget",
                "rare_disc",
                "ancient_drum",
                "bone_flute",
                "chicken_statue_artifact",
                "golden_mask",
                "golden_relic",
                "dried_starfish",
                "anchor_artifact",
                "glass_shards",
                "prehistoric_skull",
                "skeletal_hand",
                "prehistoric_rib"
        );
        assertThat(guideAliases).contains("古物", "文物", "矿物", "缺古物", "缺矿物", "全套收集");
    }

    @Test
    void allFiftyThreeMuseumMineralsAreCoveredAsResources() {
        List<String> mineralIds = List.of(
                "quartz",
                "earth_crystal",
                "frozen_tear",
                "fire_quartz",
                "emerald",
                "aquamarine",
                "ruby",
                "amethyst",
                "topaz",
                "jade",
                "diamond",
                "prismatic_shard",
                "tigerseye",
                "opal",
                "fire_opal",
                "alamite",
                "bixite",
                "baryte",
                "aerinite",
                "calcite",
                "dolomite",
                "esperite",
                "fluorapatite",
                "geminite",
                "helvite",
                "jamborite",
                "jagoite",
                "kyanite",
                "lunarite",
                "malachite",
                "neptunite",
                "lemon_stone",
                "nekoite",
                "orpiment",
                "petrified_slime",
                "thunder_egg",
                "pyrite",
                "ocean_stone",
                "ghost_crystal",
                "jasper",
                "celestine",
                "marble",
                "sandstone",
                "granite",
                "basalt",
                "limestone",
                "soapstone",
                "hematite",
                "mudstone",
                "obsidian",
                "slate",
                "fairy_stone",
                "star_shards"
        );
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(mineralIds).hasSize(53);
        assertThat(resourceIds).containsAll(mineralIds);
        assertThat(repository.resources().stream()
                .filter(resource -> mineralIds.contains(resource.getId()))
                .collect(Collectors.toList()))
                .allSatisfy(resource -> {
                    assertThat(resource.getAcquisitions()).isNotEmpty();
                    assertThat(resource.getUsedIn()).contains("博物馆捐赠");
                    assertThat(resource.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void allFortyTwoMuseumArtifactsAreCoveredAsResources() {
        List<String> artifactIds = List.of(
                "dwarf_scroll_i",
                "dwarf_scroll_ii",
                "dwarf_scroll_iii",
                "dwarf_scroll_iv",
                "chipped_amphora",
                "arrowhead",
                "ancient_doll",
                "elvish_jewelry",
                "chewing_stick",
                "ornamental_fan",
                "dinosaur_egg",
                "rare_disc",
                "ancient_sword",
                "rusty_spoon",
                "rusty_spur",
                "rusty_cog",
                "chicken_statue_artifact",
                "ancient_seed",
                "prehistoric_tool",
                "dried_starfish",
                "anchor_artifact",
                "glass_shards",
                "bone_flute",
                "prehistoric_handaxe",
                "dwarvish_helm",
                "dwarf_gadget",
                "ancient_drum",
                "golden_mask",
                "golden_relic",
                "strange_doll_green",
                "strange_doll_yellow",
                "prehistoric_scapula",
                "prehistoric_tibia",
                "prehistoric_skull",
                "skeletal_hand",
                "prehistoric_rib",
                "prehistoric_vertebra",
                "skeletal_tail",
                "nautilus_fossil",
                "amphibian_fossil",
                "palm_fossil",
                "trilobite"
        );
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(artifactIds).hasSize(42);
        assertThat(resourceIds).containsAll(artifactIds);
        assertThat(repository.resources().stream()
                .filter(resource -> artifactIds.contains(resource.getId()))
                .collect(Collectors.toList()))
                .allSatisfy(resource -> {
                    assertThat(resource.getAcquisitions()).isNotEmpty();
                    assertThat(resource.getUsedIn()).contains("博物馆捐赠");
                    assertThat(resource.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void animalProductAndFruitTreeGuidesAreCovered() {
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());
        Set<String> guideIds = repository.guides().stream()
                .map(StardewData.GuideTopic::getId)
                .collect(Collectors.toSet());

        assertThat(resourceIds).contains(
                "egg",
                "large_egg",
                "duck_egg",
                "void_egg",
                "golden_egg",
                "milk",
                "large_milk",
                "goat_milk",
                "large_goat_milk",
                "wool",
                "ostrich_egg",
                "cheese",
                "goat_cheese",
                "duck_mayonnaise",
                "void_mayonnaise",
                "apricot",
                "cherry",
                "orange",
                "peach",
                "apple",
                "pomegranate",
                "banana",
                "mango"
        );
        assertThat(guideIds).contains("animal_care", "fruit_trees");
    }

    @Test
    void commonMonsterLootResourceGuidesAreCovered() {
        Set<String> resourceIds = repository.resources().stream()
                .map(StardewData.ResourceGuide::getId)
                .collect(Collectors.toSet());

        assertThat(resourceIds).contains(
                "coal",
                "solar_essence",
                "void_essence",
                "bat_wing",
                "slime",
                "bug_meat",
                "bone_fragment"
        );
    }

    @Test
    void fullMonsterDropTableIsCovered() {
        Set<String> monsterIds = repository.monsterDrops().stream()
                .map(StardewData.MonsterDropGuide::getId)
                .collect(Collectors.toSet());

        assertThat(repository.monsterDrops()).hasSize(58);
        assertThat(monsterIds).contains(
                "monster_dust_sprite",
                "monster_serpent",
                "monster_royal_serpent",
                "monster_lava_lurk",
                "monster_pepper_rex",
                "monster_blue_squid",
                "monster_shadow_brute",
                "monster_skeleton",
                "monster_slimes",
                "monster_bats",
                "monster_wilderness_golem"
        );
        assertThat(repository.findMonsterDrop("煤尘精灵掉什么")).hasValueSatisfying(monster ->
                assertThat(monster.getDrops()).contains("煤炭 (50%)"));
        assertThat(repository.findMonsterDrop("飞蛇在哪刷")).hasValueSatisfying(monster -> {
            assertThat(monster.getName()).isEqualTo("飞蛇");
            assertThat(monster.getLocations()).contains("骷髅洞穴");
        });
        assertThat(repository.findMonsterDrop("熔岩潜伏怪掉落")).hasValueSatisfying(monster ->
                assertThat(monster.getDrops()).contains("龙牙 (15%)"));
    }

    @Test
    void allMonsterDropGuidesHaveCoreFieldsAndSources() {
        assertThat(repository.monsterDrops())
                .allSatisfy(monster -> {
                    assertThat(monster.getId()).isNotBlank();
                    assertThat(monster.getName()).isNotBlank();
                    assertThat(monster.getAliases()).isNotEmpty();
                    assertThat(monster.getLocations()).isNotEmpty();
                    assertThat(monster.getDrops()).isNotEmpty();
                    assertThat(monster.getRecommendation()).isNotBlank();
                    assertThat(monster.getSourceUrls()).isNotEmpty();
                });
    }

    @Test
    void allResourceGuidesHaveAcquisitionRecommendationAndSources() {
        assertThat(repository.resources())
                .allSatisfy(resource -> {
                    assertThat(resource.getId()).isNotBlank();
                    assertThat(resource.getName()).isNotBlank();
                    assertThat(resource.getAcquisitions()).isNotEmpty();
                    assertThat(resource.getRecommendation()).isNotBlank();
                    assertThat(resource.getUsedIn()).isNotEmpty();
                    assertThat(resource.getSourceUrls()).isNotEmpty();
                });
    }
}
