package com.bb.bot.handler.stardew;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StardewGuideService {

    private static final Pattern HH_MM = Pattern.compile("([01]?\\d|2[0-3])[:：]([0-5]\\d)");
    private static final Pattern HOUR_ZH = Pattern.compile("(上午|中午|下午|晚上|凌晨)?\\s*(\\d{1,2})\\s*[点时]");
    private static final Pattern DAY = Pattern.compile("(春季|夏季|秋季|冬季|春天|夏天|秋天|冬天|春|夏|秋|冬)?\\s*(\\d{1,2})\\s*[日号]");

    private final StardewKnowledgeRepository repository;
    private final StardewWikiClient wikiClient;

    public StardewGuideService(StardewKnowledgeRepository repository) {
        this(repository, null);
    }

    @Autowired
    public StardewGuideService(StardewKnowledgeRepository repository, StardewWikiClient wikiClient) {
        this.repository = repository;
        this.wikiClient = wikiClient;
    }

    public StardewGuideResult helpAnswer() {
        return result("help", help(), List.of());
    }

    /**
     * Compatibility entry point for older direct callers. The normal assistant path should use
     * {@link StardewQueryPlannerService} + {@link StardewGuideRetriever} and call
     * {@link #answerEvidence(StardewGuideIntent, String)} with an explicit intent.
     */
    @Deprecated
    public StardewGuideResult answer(String rawQuery) {
        String query = cleanQuery(rawQuery);
        if (StringUtils.isBlank(query)) {
            return helpAnswer();
        }

        Optional<StardewData.Bundle> bundle = repository.findBundle(query);
        Optional<StardewData.Villager> villager = repository.findVillager(query);
        Optional<StardewData.Fish> fish = repository.findFish(query);
        Optional<StardewData.Crop> crop = repository.findCrop(query);
        Optional<StardewData.Building> building = repository.findBuilding(query);
        Optional<StardewData.Tool> tool = repository.findTool(query);
        Optional<StardewData.Machine> machine = repository.findMachine(query);
        Optional<StardewData.Shop> shop = repository.findShop(query);
        Optional<ShopStockMatch> shopItem = findShopStock(query);
        Optional<StardewData.ResourceGuide> resource = repository.findResource(query);
        Optional<StardewData.MonsterDropGuide> monsterDrop = repository.findMonsterDrop(query);
        Optional<StardewData.FishPondGuide> fishPond = repository.findFishPond(query);
        Optional<StardewData.CookingRecipe> cookingRecipe = repository.findCookingRecipe(query);
        Optional<StardewData.SpecialOrderGuide> specialOrder = repository.findSpecialOrder(query);
        Optional<StardewData.SkillGuide> skillGuide = repository.findSkillGuide(query);
        Optional<StardewData.FestivalEvent> festivalEvent = repository.findFestivalEvent(query);
        Optional<StardewData.GuideTopic> guide = repository.findGuide(query);
        List<StardewData.BookGuide> books = repository.findBooks(query);

        if (resource.isPresent() && looksLikeSpecificSpecialCurrencyResourceQuery(query)) {
            return resourceAnswer(query, resource.get());
        }
        boolean bundleIntent = query.contains("收集包") || query.contains("献祭")
                || (bundle.isPresent() && (query.contains("要") || query.contains("需要") || query.contains("交") || query.contains("包")));
        if (bundleIntent && !(resource.isPresent() && looksLikeResourceQuery(query))
                && (bundle.isPresent() || (crop.isEmpty() && building.isEmpty()))) {
            return bundle.map(b -> bundleAnswer(query, b))
                    .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应收集包。可以试试：海鱼收集包、湖鱼收集包、工匠收集包、锅炉房。"));
        }
        if (guide.isPresent() && shouldPreferForgeGuide(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (tool.isPresent() || looksLikeToolQuery(query)) {
            if (tool.isPresent()) {
                return toolDetailAnswer(query, tool.get());
            }
            return toolListAnswer();
        }
        if (!books.isEmpty() && shouldPreferBookGuideOverShop(query)) {
            return bookAnswer(query, books);
        }
        if (guide.isPresent() && shouldPreferGuideOverShop(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (looksLikeShopQuery(query) && shopItem.isPresent()) {
            return shopItemAnswer(query, shopItem.get());
        }
        if (looksLikeShopQuery(query) && !books.isEmpty()) {
            return bookAnswer(query, books);
        }
        if (looksLikeShopQuery(query) && shop.isPresent()) {
            return shopAnswer(shop.get());
        }
        if (guide.isPresent() && shouldPreferGuideOverResource(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (resource.isPresent() && looksLikeResourceQuery(query)
                && !(fish.isPresent() && query.contains("果冻"))
                && !isFishingQuestion(query)
                && (!isMachineCraftingQuestion(query, machine) || shouldPreferResourceOverMachine(query, resource.get(), machine))) {
            return resourceAnswer(query, resource.get());
        }
        if (monsterDrop.isPresent() && looksLikeMonsterDropQuery(query)) {
            return monsterDropAnswer(monsterDrop.get());
        }
        if (looksLikeFishPondQuery(query) && !looksLikeFishPondBuildingQuery(query)) {
            if (fishPond.isPresent() && !isBroadFishPondQuery(query)) {
                return fishPondDetailAnswer(fishPond.get());
            }
            return fishPondListAnswer(query);
        }
        if (specialOrder.isPresent() && looksLikeSpecialOrderQuery(query)) {
            return specialOrderAnswer(specialOrder.get());
        }
        if (skillGuide.isPresent() && looksLikeSkillGuideQuery(query)) {
            if (isBroadSkillGuideQuery(query)) {
                return skillGuideListAnswer(query);
            }
            return skillGuideAnswer(skillGuide.get());
        }
        if ((festivalEvent.isPresent() || looksLikeFestivalQuery(query)) && !isFishingQuestion(query)) {
            if (festivalEvent.isPresent() && !isBroadFestivalQuery(query)) {
                return festivalDetailAnswer(festivalEvent.get());
            }
            return festivalListAnswer(query);
        }
        if (villager.isPresent() && looksLikeScheduleQuery(query)) {
            return villagerAnswer(query, villager.get());
        }
        if (villager.isPresent() && looksLikeVillagerProfileQuery(query)) {
            return villagerProfileAnswer(villager.get());
        }
        if (guide.isPresent() && shouldPreferGuideOverBuilding(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (building.isPresent() || looksLikeBuildingQuery(query)) {
            boolean broadBuildingQuery = isBroadBuildingQuery(query);
            if (building.isPresent() && !broadBuildingQuery) {
                return buildingDetailAnswer(building.get());
            }
            return buildingListAnswer(query);
        }
        if ((machine.isPresent() || looksLikeMachineQuery(query)) && !isCrabPotCatchQuery(query)) {
            boolean broadMachineQuery = isBroadMachineQuery(query);
            if (machine.isPresent() && !broadMachineQuery) {
                return machineDetailAnswer(machine.get());
            }
            return machineListAnswer(query);
        }
        if (!books.isEmpty() && looksLikeBookDetailQuery(query)) {
            return bookAnswer(query, books);
        }
        if (guide.isPresent() && shouldPreferGuideOverCooking(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (cookingRecipe.isPresent() && looksLikeCookingQuery(query) && !isBroadCookingQuery(query)) {
            return cookingRecipeAnswer(cookingRecipe.get());
        }
        if (looksLikeCookingQuery(query)) {
            return cookingListAnswer(query, cookingRecipe);
        }
        if (guide.isPresent() && shouldPreferGuideOverCrop(query, guide.get())) {
            return guideAnswer(guide.get());
        }
        if (crop.isPresent() || looksLikeCropQuery(query)) {
            boolean broadCropQuery = isBroadCropQuery(query);
            if (crop.isPresent() && !broadCropQuery) {
                return cropDetailAnswer(crop.get());
            }
            return cropListAnswer(query);
        }
        if (guide.isPresent() || looksLikeGuideQuery(query)) {
            if (!books.isEmpty() && looksLikeBookDetailQuery(query)) {
                return bookAnswer(query, books);
            }
            return guide.map(this::guideAnswer)
                    .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应攻略条目。可以试试：斧头升级、鱼竿、鸡舍、畜棚、筒仓、房屋升级、夏季作物。"));
        }
        if (query.contains("钓") || query.contains("鱼") || query.contains("蟹笼")
                || query.contains("果冻") || fish.isPresent()) {
            boolean broadFishQuery = isBroadFishQuery(query);
            if (fish.isPresent() && !broadFishQuery) {
                return specificFishAnswer(query, fish.get());
            }
            return fishListAnswer(query);
        }
        if (resource.isPresent() || looksLikeResourceQuery(query)) {
            return resource.map(r -> resourceAnswer(query, r))
                    .orElseGet(() -> wikiFallbackAnswer(query, "没找到这个资源。可以试试：硬木、电池组、铱矿石、五彩碎片、上古种子。"));
        }

        return wikiFallbackAnswer(query, help());
    }

    @Deprecated
    public StardewGuideResult answer(StardewGuideIntent type, String rawQuery) {
        return answerEvidence(type, rawQuery);
    }

    public StardewGuideResult answerEvidence(StardewGuideIntent type, String rawQuery) {
        String query = cleanQuery(rawQuery);
        if (StringUtils.isBlank(query)) {
            return helpAnswer();
        }
        StardewGuideIntent effectiveType = type == null ? StardewGuideIntent.UNKNOWN : type;
        return switch (effectiveType) {
            case FISH -> typedFishAnswer(query);
            case BUNDLE -> typedBundleAnswer(query);
            case VILLAGER_SCHEDULE -> typedVillagerScheduleAnswer(query);
            case VILLAGER_PROFILE -> typedVillagerProfileAnswer(query);
            case RESOURCE -> typedResourceAnswer(query);
            case MONSTER_DROP -> typedMonsterDropAnswer(query);
            case FISH_POND -> typedFishPondAnswer(query);
            case ANIMAL_CARE -> typedAnimalCareAnswer(query);
            case FRUIT_TREE, MUSEUM, GUIDE -> typedGuideAnswer(query);
            case SKILL -> typedSkillAnswer(query);
            case CROP -> typedCropAnswer(query);
            case TOOL -> typedToolAnswer(query);
            case BUILDING -> typedBuildingAnswer(query);
            case CRAFTING -> typedCraftingAnswer(query);
            case MACHINE -> typedMachineAnswer(query);
            case SHOP -> typedShopAnswer(query);
            case COOKING -> typedCookingAnswer(query);
            case QUEST -> typedStoryQuestAnswer(query);
            case SPECIAL_ORDER -> typedSpecialOrderAnswer(query);
            case FESTIVAL -> typedFestivalAnswer(query);
            case FARM_MAP -> typedFarmMapAnswer(query);
            case ISLAND -> typedIslandAnswer(query);
            case DUNGEON -> typedDungeonAnswer(query);
            case UNKNOWN -> result("unknown", "", List.of());
        };
    }

    private StardewGuideResult typedFishAnswer(String query) {
        Optional<StardewData.Fish> fish = repository.findFish(query);
        if (fish.isPresent() && !isBroadFishQuery(query)) {
            return specificFishAnswer(query, fish.get());
        }
        return fishListAnswer(query);
    }

    private StardewGuideResult typedBundleAnswer(String query) {
        return repository.findBundle(query)
                .map(bundle -> bundleAnswer(query, bundle))
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应收集包。可以试试：海鱼收集包、湖鱼收集包、工匠收集包、锅炉房。"));
    }

    private StardewGuideResult typedVillagerScheduleAnswer(String query) {
        return repository.findVillager(query)
                .map(villager -> villagerAnswer(query, villager))
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应居民。可以补充居民名、季节、日期和游戏内时间。"));
    }

    private StardewGuideResult typedVillagerProfileAnswer(String query) {
        return repository.findVillager(query)
                .map(this::villagerProfileAnswer)
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应居民资料。可以试试：阿比盖尔喜欢什么、莉亚生日。"));
    }

    private StardewGuideResult typedResourceAnswer(String query) {
        return repository.findResource(query)
                .map(resource -> resourceAnswer(query, resource))
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到这个资源。可以试试：硬木、电池组、铱矿石、五彩碎片、上古种子。"));
    }

    private StardewGuideResult typedMonsterDropAnswer(String query) {
        return repository.findMonsterDrop(query)
                .map(this::monsterDropAnswer)
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应怪物掉落。可以试试：煤尘精灵掉什么、飞蛇掉什么、熔岩潜伏怪掉什么。"));
    }

    private StardewGuideResult typedFishPondAnswer(String query) {
        Optional<StardewData.FishPondGuide> fishPond = repository.findFishPond(query);
        if (fishPond.isPresent() && !isBroadFishPondQuery(query)) {
            return fishPondDetailAnswer(fishPond.get());
        }
        return fishPondListAnswer(query);
    }

    private StardewGuideResult typedGuideAnswer(String query) {
        List<StardewData.BookGuide> books = repository.findBooks(query);
        if (!books.isEmpty() && !isBroadBookQuery(query)) {
            return bookAnswer(query, books);
        }
        return repository.findGuide(query)
                .map(this::guideAnswer)
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应攻略条目。可以试试：战斗技能、博物馆捐赠、动物养殖、果树。"));
    }

    private StardewGuideResult typedSkillAnswer(String query) {
        Optional<StardewData.SkillGuide> skillGuide = repository.findSkillGuide(query);
        if (skillGuide.isPresent() && !isBroadSkillGuideQuery(query)) {
            return skillGuideAnswer(skillGuide.get());
        }
        if (isBroadSkillGuideQuery(query)) {
            return skillGuideListAnswer(query);
        }
        return skillGuide
                .map(this::skillGuideAnswer)
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应技能攻略。可以试试：战斗技能、钓鱼等级、职业重置、技能书。"));
    }

    private StardewGuideResult typedCropAnswer(String query) {
        Optional<StardewData.Crop> crop = repository.findCrop(query);
        if (crop.isPresent() && !isBroadCropQuery(query)) {
            return cropDetailAnswer(crop.get());
        }
        return cropListAnswer(query);
    }

    private StardewGuideResult typedToolAnswer(String query) {
        return repository.findTool(query)
                .map(tool -> toolDetailAnswer(query, tool))
                .orElseGet(this::toolListAnswer);
    }

    private StardewGuideResult typedBuildingAnswer(String query) {
        Optional<StardewData.Building> building = repository.findBuilding(query);
        if (building.isPresent() && !isBroadBuildingQuery(query)) {
            return buildingDetailAnswer(building.get());
        }
        return buildingListAnswer(query);
    }

    private StardewGuideResult typedMachineAnswer(String query) {
        Optional<StardewData.CraftingRecipe> recipe = repository.findCraftingRecipe(query);
        if (recipe.isPresent() && !isBroadMachineQuery(query)) {
            return machineDetailAnswer(recipe.get());
        }
        Optional<StardewData.Machine> machine = repository.findMachine(query);
        if (machine.isPresent() && !isBroadMachineQuery(query)) {
            return machineDetailAnswer(machine.get());
        }
        return machineListAnswer(query);
    }

    private StardewGuideResult typedCraftingAnswer(String query) {
        Optional<StardewData.CraftingRecipe> recipe = repository.findCraftingRecipe(query);
        if (recipe.isPresent() && !isBroadCraftingQuery(query)) {
            return craftingDetailAnswer(recipe.get());
        }
        return craftingListAnswer(query);
    }

    private StardewGuideResult typedShopAnswer(String query) {
        Optional<ShopStockMatch> shopItem = findShopStock(query);
        if (shopItem.isPresent()) {
            return shopItemAnswer(query, shopItem.get());
        }
        List<StardewData.BookGuide> books = repository.findBooks(query);
        if (!books.isEmpty()) {
            return bookAnswer(query, books);
        }
        return repository.findShop(query)
                .map(this::shopAnswer)
                .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应商店或商品。可以试试：背包升级、铱制洒水器在哪里买、书商什么时候来。"));
    }

    private StardewGuideResult typedCookingAnswer(String query) {
        Optional<StardewData.CookingRecipe> cookingRecipe = repository.findCookingRecipe(query);
        if (cookingRecipe.isPresent()
                && (!isBroadCookingQuery(query) || looksLikeSpecificCookingRecipeQuery(query))) {
            return cookingRecipeAnswer(cookingRecipe.get());
        }
        return cookingListAnswer(query, cookingRecipe);
    }

    private StardewGuideResult typedStoryQuestAnswer(String query) {
        Optional<StardewData.StoryQuestGuide> storyQuest = repository.findStoryQuest(query);
        if (storyQuest.isPresent() && !isBroadStoryQuestQuery(query)) {
            return storyQuestAnswer(storyQuest.get());
        }
        return storyQuestListAnswer(query, storyQuest);
    }

    private StardewGuideResult typedSpecialOrderAnswer(String query) {
        Optional<StardewData.SpecialOrderGuide> specialOrder = repository.findSpecialOrder(query);
        if (specialOrder.isPresent() && !isBroadSpecialOrderQuery(query)) {
            return specialOrderAnswer(specialOrder.get());
        }
        return specialOrderListAnswer(query, specialOrder);
    }

    private StardewGuideResult typedFestivalAnswer(String query) {
        Optional<StardewData.FestivalEvent> event = repository.findFestivalEvent(query);
        if (event.isPresent() && !isBroadFestivalQuery(query)) {
            return festivalDetailAnswer(event.get());
        }
        return festivalListAnswer(query);
    }

    private StardewGuideResult typedFarmMapAnswer(String query) {
        Optional<StardewData.FarmMapGuide> farmMap = repository.findFarmMap(query);
        if (farmMap.isPresent() && !isBroadFarmMapQuery(query)) {
            return farmMapDetailAnswer(farmMap.get());
        }
        return farmMapListAnswer(query, farmMap);
    }

    private StardewGuideResult typedIslandAnswer(String query) {
        Optional<StardewData.IslandGuide> island = repository.findIslandGuide(query);
        if (island.isPresent() && !isBroadIslandQuery(query)) {
            return islandDetailAnswer(island.get());
        }
        return islandListAnswer(query, island);
    }

    private StardewGuideResult typedDungeonAnswer(String query) {
        Optional<StardewData.DungeonGuide> dungeon = repository.findDungeonGuide(query);
        if (dungeon.isPresent() && !isBroadDungeonQuery(query)) {
            return dungeonDetailAnswer(dungeon.get());
        }
        return dungeonListAnswer(query, dungeon);
    }

    private StardewGuideResult typedAnimalCareAnswer(String query) {
        Optional<StardewData.FarmAnimalGuide> animal = repository.findFarmAnimal(query);
        if (looksLikeSharedFarmAnimalProductQuery(query)) {
            return farmAnimalListAnswer(query, animal);
        }
        if (animal.isPresent() && !isBroadFarmAnimalQuery(query)) {
            return farmAnimalDetailAnswer(animal.get());
        }
        if (looksLikeFarmAnimalListQuery(query)) {
            return farmAnimalListAnswer(query, animal);
        }
        if (looksLikeAnimalCareGuideQuery(query)) {
            return typedGuideAnswer(query);
        }
        return animal.map(this::farmAnimalDetailAnswer)
                .orElseGet(() -> farmAnimalListAnswer(query, Optional.empty()));
    }

    private boolean looksLikeSpecificCookingRecipeQuery(String query) {
        return query.contains("怎么做") || query.contains("材料") || query.contains("配方")
                || query.contains("效果") || query.contains("来源");
    }

    private StardewGuideResult guideAnswer(StardewData.GuideTopic guide) {
        StringBuilder sb = new StringBuilder();
        sb.append(guide.getName()).append("：\n");
        for (StardewData.GuideSection section : guide.getSections()) {
            if (StringUtils.isNotBlank(section.getTitle())) {
                sb.append(section.getTitle()).append("：\n");
            }
            for (String line : section.getLines()) {
                sb.append("- ").append(line).append("\n");
            }
        }
        if (StringUtils.isNotBlank(guide.getRecommendation())) {
            sb.append("建议：").append(guide.getRecommendation()).append("\n");
        }
        return result("guide", sb.toString().trim(), guide.getSourceUrls());
    }

    private StardewGuideResult skillGuideAnswer(StardewData.SkillGuide guide) {
        StringBuilder sb = new StringBuilder();
        sb.append(guide.getName()).append("：\n");
        for (StardewData.GuideSection section : guide.getSections()) {
            if (StringUtils.isNotBlank(section.getTitle())) {
                sb.append(section.getTitle()).append("：\n");
            }
            for (String line : section.getLines()) {
                sb.append("- ").append(line).append("\n");
            }
        }
        if (StringUtils.isNotBlank(guide.getRecommendation())) {
            sb.append("建议：").append(guide.getRecommendation()).append("\n");
        }
        return result("skill_guide", sb.toString().trim(), guide.getSourceUrls());
    }

    private StardewGuideResult skillGuideListAnswer(String query) {
        StringBuilder sb = new StringBuilder("技能攻略对照：\n");
        for (StardewData.SkillGuide guide : repository.skillGuides()) {
            sb.append("- ").append(guide.getName()).append("：");
            sb.append(StringUtils.defaultIfBlank(guide.getRecommendation(), "可问具体技能查看升级路线、职业和技巧。"));
            sb.append("\n");
        }
        sb.append("建议：问具体技能可以看经验来源、升级路线、职业选择和推荐装备，例如“战斗等级低怎么升级”“钓鱼职业怎么选”。");
        List<String> sources = repository.skillGuides().stream()
                .flatMap(guide -> guide.getSourceUrls().stream())
                .distinct()
                .toList();
        return result("skill_guide_list", sb.toString().trim(), sources);
    }

    private StardewGuideResult specialOrderAnswer(StardewData.SpecialOrderGuide order) {
        StringBuilder sb = new StringBuilder();
        sb.append(order.getName()).append("特别订单：\n");
        if (StringUtils.isNotBlank(order.getBoard())) {
            sb.append("任务板：").append(order.getBoard()).append("\n");
        }
        if (StringUtils.isNotBlank(order.getRequester())) {
            sb.append("委托人：").append(order.getRequester()).append("\n");
        }
        if (StringUtils.isNotBlank(order.getPrerequisite())) {
            sb.append("前置条件：").append(order.getPrerequisite()).append("\n");
        }
        if (StringUtils.isNotBlank(order.getTimeframe())) {
            sb.append("期限：").append(order.getTimeframe()).append("\n");
        }
        appendLines(sb, "需求", order.getRequirements());
        appendLines(sb, "奖励", order.getRewards());
        if (StringUtils.isNotBlank(order.getRepeatable())) {
            sb.append("可重复：").append(order.getRepeatable()).append("\n");
        }
        appendLines(sb, "要点", order.getTips());
        if (StringUtils.isNotBlank(order.getRecommendation())) {
            sb.append("建议：").append(order.getRecommendation()).append("\n");
        }
        return result("special_order", sb.toString().trim(), order.getSourceUrls());
    }

    private StardewGuideResult specialOrderListAnswer(String query, Optional<StardewData.SpecialOrderGuide> preferred) {
        String normalized = StardewKnowledgeRepository.normalize(query);
        String boardFilter = containsAny(normalized, "齐先生", "齐钻", "核桃房", "qi")
                ? "齐先生核桃房特别订单板"
                : containsAny(normalized, "城镇", "鹈鹕镇", "刘易斯", "特别订单板")
                ? "鹈鹕镇特别订单板"
                : "";
        List<StardewData.SpecialOrderGuide> orders = repository.specialOrders().stream()
                .filter(order -> StringUtils.isBlank(boardFilter) || boardFilter.equals(order.getBoard()))
                .toList();
        if (orders.isEmpty()) {
            return preferred.map(this::specialOrderAnswer)
                    .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应特别订单。可以试试：罗宾资源冲刺、岛屿食材、齐瓜、五彩农场。"));
        }
        StringBuilder sb = new StringBuilder("特别订单对照：\n");
        appendSpecialOrderGroup(sb, "鹈鹕镇特别订单板", orders);
        appendSpecialOrderGroup(sb, "齐先生核桃房特别订单板", orders);
        sb.append(specialOrderExampleSuggestion(boardFilter));
        return result("special_order_list", sb.toString().trim(),
                List.of("https://stardewvalleywiki.com/Quests", "https://stardewvalleywiki.com/Qi%27s_Walnut_Room"));
    }

    private String specialOrderExampleSuggestion(String boardFilter) {
        if ("鹈鹕镇特别订单板".equals(boardFilter)) {
            return "建议：问具体订单名可以看完整需求、奖励和做法，例如“罗宾资源冲刺奖励是什么”“岛屿食材要什么”。";
        }
        if ("齐先生核桃房特别订单板".equals(boardFilter)) {
            return "建议：问具体订单名可以看完整需求、奖励和做法，例如“齐瓜怎么做”“五彩农场交什么”。";
        }
        return "建议：问具体订单名可以看完整需求、奖励和做法，例如“罗宾资源冲刺奖励是什么”“齐瓜怎么做”。";
    }

    private void appendSpecialOrderGroup(StringBuilder sb, String board, List<StardewData.SpecialOrderGuide> orders) {
        List<StardewData.SpecialOrderGuide> group = orders.stream()
                .filter(order -> board.equals(order.getBoard()))
                .toList();
        if (group.isEmpty()) {
            return;
        }
        sb.append(board).append("：\n");
        for (StardewData.SpecialOrderGuide order : group) {
            sb.append("- ").append(order.getName()).append("：")
                    .append(order.getTimeframe()).append("；")
                    .append(firstOrDefault(order.getRequirements(), "查看具体订单获取需求")).append("；奖励 ")
                    .append(firstOrDefault(order.getRewards(), "见订单详情")).append("\n");
        }
    }

    private StardewGuideResult festivalDetailAnswer(StardewData.FestivalEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.getName()).append("：\n")
                .append("时间：").append(formatFestivalDate(event)).append("，")
                .append(StringUtils.defaultIfBlank(event.getEntryTime(), "入场时间未记录")).append("\n")
                .append("地点：").append(StringUtils.defaultIfBlank(event.getLocation(), "暂未记录")).append("\n");
        if (StringUtils.isNotBlank(event.getEndTime())) {
            sb.append("结束：").append(event.getEndTime());
            if (StringUtils.isNotBlank(event.getReturnTime())) {
                sb.append("，结束/离开后回到农场约 ").append(event.getReturnTime());
            }
            sb.append("\n");
        }
        sb.append("时间流逝：").append(Boolean.TRUE.equals(event.getTimePasses()) ? "会继续流逝" : "进入后时间基本暂停/结束后跳到固定时间").append("\n");
        sb.append("店铺和房屋：").append(Boolean.TRUE.equals(event.getShopsClosed()) ? "大多关闭" : "通常照常开放").append("\n");
        sb.append("动物喂食：").append(Boolean.TRUE.equals(event.getAnimalsNeedFeeding()) ? "仍要正常喂" : "通常会自动视为已喂").append("\n");
        appendLines(sb, "主要玩法", event.getActivities());
        appendLines(sb, "奖励/重点物品", event.getRewards());
        appendLines(sb, "商店/兑换重点", event.getShopHighlights());
        if (StringUtils.isNotBlank(event.getRecommendation())) {
            sb.append("建议：").append(event.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(event.getNote())) {
            sb.append("提示：").append(event.getNote()).append("\n");
        }
        return result("festival_detail", sb.toString().trim(), event.getSourceUrls());
    }

    private StardewGuideResult festivalListAnswer(String query) {
        QueryContext ctx = parseContext(query);
        List<StardewData.FestivalEvent> matched = repository.festivalEvents().stream()
                .filter(event -> ctx.season == null || ctx.season.equals(event.getSeason()))
                .sorted(Comparator.comparing((StardewData.FestivalEvent event) -> seasonOrder(event.getSeason()))
                        .thenComparing(event -> event.getStartDay() == null ? Integer.MAX_VALUE : event.getStartDay())
                        .thenComparing(StardewData.FestivalEvent::getName))
                .toList();
        if (matched.isEmpty()) {
            return result("festival_available", "按当前条件没匹配到节日。可以试试：春季节日、沙漠节怎么玩、夜市潜艇、冬星盛宴送什么。", List.of());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.season == null ? "全年节日/活动" : ctx.season + "节日/活动").append("：\n");
        for (StardewData.FestivalEvent event : matched) {
            sb.append("- ").append(event.getName()).append("：")
                    .append(formatFestivalDate(event)).append("，")
                    .append(StringUtils.defaultIfBlank(event.getEntryTime(), "入场时间未记录")).append("，")
                    .append(StringUtils.defaultIfBlank(event.getLocation(), "地点未记录"));
            if (event.getActivities() != null && !event.getActivities().isEmpty()) {
                sb.append("；").append(event.getActivities().get(0));
            }
            if (StringUtils.isNotBlank(event.getRecommendation())) {
                sb.append("；建议：").append(event.getRecommendation());
            }
            sb.append("\n");
        }
        sb.append("建议：问具体节日名可以看玩法、奖励、商店重点和是否需要喂动物，例如“沙漠节怎么玩”“星露谷展览会怎么拿星之果实”。");
        return result("festival_available", sb.toString().trim(), collectFestivalSources(matched));
    }

    private StardewGuideResult farmMapDetailAnswer(StardewData.FarmMapGuide map) {
        StringBuilder sb = new StringBuilder();
        sb.append(map.getName()).append("：\n");
        if (StringUtils.isNotBlank(map.getLayoutSummary())) {
            sb.append("布局：").append(map.getLayoutSummary()).append("\n");
        }
        sb.append("关联方向：").append(formatList(map.getAssociatedSkills(), "无固定方向")).append("\n");
        sb.append("可耕地：").append(map.getTillableTiles() == null ? "未记录" : map.getTillableTiles() + " 格")
                .append("，不可耕但可建造：")
                .append(map.getNonTillableBuildableTiles() == null ? "未记录" : map.getNonTillableBuildableTiles() + " 格")
                .append("\n");
        appendLines(sb, "优势", map.getPerks());
        appendLines(sb, "限制", map.getLimits());
        appendLines(sb, "钓鱼/水域", map.getFishing());
        appendLines(sb, "适合", map.getBestFor());
        appendLines(sb, "建议", map.getRecommendations());
        if (StringUtils.isNotBlank(map.getNote())) {
            sb.append("提示：").append(map.getNote()).append("\n");
        }
        return result("farm_map_detail", sb.toString().trim(), map.getSourceUrls());
    }

    private StardewGuideResult farmMapListAnswer(String query, Optional<StardewData.FarmMapGuide> preferred) {
        List<StardewData.FarmMapGuide> matched = repository.farmMaps().stream()
                .filter(map -> matchesFarmMapQuery(query, map))
                .toList();
        if (matched.isEmpty()) {
            matched = preferred.map(List::of).orElseGet(repository::farmMaps);
        }
        if (matched.isEmpty()) {
            return result("farm_map_available", "没找到农场地图资料。可以试试：开局农场怎么选、海滩农场、草原农场。", List.of());
        }

        StringBuilder sb = new StringBuilder("农场地图选择：\n");
        for (StardewData.FarmMapGuide map : matched) {
            sb.append("- ").append(map.getName()).append("：")
                    .append(StringUtils.defaultIfBlank(map.getLayoutSummary(), "查看详情了解布局特点"));
            if (map.getBestFor() != null && !map.getBestFor().isEmpty()) {
                sb.append("；适合：").append(String.join("、", map.getBestFor()));
            }
            if (map.getRecommendations() != null && !map.getRecommendations().isEmpty()) {
                sb.append("；建议：").append(map.getRecommendations().get(0));
            }
            sb.append("\n");
        }
        sb.append("建议：新手和自动化优先标准/草原；想钓鱼、采矿、硬木、战斗或多人分区，再选对应主题农场。问具体农场名可以看限制和细节。");
        return result("farm_map_available", sb.toString().trim(), collectFarmMapSources(matched));
    }

    private StardewGuideResult farmAnimalDetailAnswer(StardewData.FarmAnimalGuide animal) {
        StringBuilder sb = new StringBuilder();
        sb.append(animal.getName()).append("：\n")
                .append("建筑：").append(StringUtils.defaultIfBlank(animal.getBuilding(), "未记录")).append("\n")
                .append("获取：").append(StringUtils.defaultIfBlank(animal.getAcquisition(), "未记录")).append("\n");
        if (animal.getPurchasePrice() != null) {
            sb.append("购买价格：").append(formatGold(animal.getPurchasePrice())).append("\n");
        }
        if (animal.getMaturityDays() != null) {
            sb.append("成熟：").append(animal.getMaturityDays() == 0 ? "出生即成熟" : animal.getMaturityDays() + " 天").append("\n");
        }
        if (StringUtils.isNotBlank(animal.getProduceFrequency())) {
            sb.append("产出频率：").append(animal.getProduceFrequency()).append("\n");
        }
        if (StringUtils.isNotBlank(animal.getToolRequired())) {
            sb.append("工具/采集：").append(animal.getToolRequired()).append("\n");
        }
        appendAnimalProducts(sb, animal.getProducts());
        appendLines(sb, "机制", animal.getMechanics());
        appendLines(sb, "适合", animal.getBestFor());
        appendLines(sb, "建议", animal.getRecommendations());
        if (StringUtils.isNotBlank(animal.getNote())) {
            sb.append("提示：").append(animal.getNote()).append("\n");
        }
        return result("farm_animal_detail", sb.toString().trim(), animal.getSourceUrls());
    }

    private StardewGuideResult farmAnimalListAnswer(String query, Optional<StardewData.FarmAnimalGuide> preferred) {
        List<StardewData.FarmAnimalGuide> matched = repository.farmAnimals().stream()
                .filter(animal -> matchesFarmAnimalQuery(query, animal))
                .toList();
        if (matched.isEmpty()) {
            matched = preferred.map(List::of).orElseGet(repository::farmAnimals);
        }
        if (matched.isEmpty()) {
            return result("farm_animal_available", "没找到农场动物资料。可以试试：鸡、奶牛、鸭、兔子、猪、鸵鸟。", List.of());
        }
        StringBuilder sb = new StringBuilder("农场动物对照：\n");
        for (StardewData.FarmAnimalGuide animal : matched) {
            sb.append("- ").append(animal.getName()).append("：")
                    .append(StringUtils.defaultIfBlank(animal.getBuilding(), "建筑未记录"));
            if (animal.getPurchasePrice() != null) {
                sb.append("，").append(formatGold(animal.getPurchasePrice()));
            }
            if (animal.getProducts() != null && !animal.getProducts().isEmpty()) {
                sb.append("；产物：")
                        .append(animal.getProducts().stream().map(this::animalProductSummary).limit(3).reduce((a, b) -> a + "、" + b).orElse("未记录"));
            }
            if (animal.getRecommendations() != null && !animal.getRecommendations().isEmpty()) {
                sb.append("；建议：").append(animal.getRecommendations().get(0));
            }
            sb.append("\n");
        }
        sb.append("建议：前期鸡和奶牛最稳；中期按收集包补鸭、兔、山羊；后期赚钱重点看猪，姜岛后再考虑鸵鸟。问具体动物名可看产物、成熟时间和机制。");
        return result("farm_animal_available", sb.toString().trim(), collectFarmAnimalSources(matched));
    }

    private StardewGuideResult dungeonDetailAnswer(StardewData.DungeonGuide dungeon) {
        StringBuilder sb = new StringBuilder();
        sb.append(dungeon.getName()).append("：\n");
        if (StringUtils.isNotBlank(dungeon.getLocation())) {
            sb.append("位置：").append(dungeon.getLocation()).append("\n");
        }
        if (StringUtils.isNotBlank(dungeon.getUnlock())) {
            sb.append("解锁：").append(dungeon.getUnlock()).append("\n");
        }
        if (StringUtils.isNotBlank(dungeon.getFloorSummary())) {
            sb.append("结构：").append(dungeon.getFloorSummary()).append("\n");
        }
        appendLines(sb, "机制", dungeon.getMechanics());
        appendLines(sb, "怪物/威胁", dungeon.getMonsters());
        appendLines(sb, "奖励/产出", dungeon.getLoot());
        appendLines(sb, "相关任务", dungeon.getQuests());
        appendLines(sb, "建议", dungeon.getRecommendations());
        if (StringUtils.isNotBlank(dungeon.getNote())) {
            sb.append("提示：").append(dungeon.getNote()).append("\n");
        }
        return result("dungeon_detail", sb.toString().trim(), dungeon.getSourceUrls());
    }

    private StardewGuideResult islandDetailAnswer(StardewData.IslandGuide island) {
        StringBuilder sb = new StringBuilder();
        sb.append(island.getName()).append("：\n");
        if (StringUtils.isNotBlank(island.getRegion())) {
            sb.append("区域：").append(island.getRegion()).append("\n");
        }
        if (StringUtils.isNotBlank(island.getUnlock())) {
            sb.append("解锁：").append(island.getUnlock()).append("\n");
        }
        if (StringUtils.isNotBlank(island.getOverview())) {
            sb.append("概览：").append(island.getOverview()).append("\n");
        }
        appendLines(sb, "可做事项", island.getActivities());
        appendLines(sb, "金核桃/解锁", island.getWalnuts());
        appendLines(sb, "奖励/产出", island.getRewards());
        appendLines(sb, "建议", island.getRecommendations());
        if (StringUtils.isNotBlank(island.getNote())) {
            sb.append("提示：").append(island.getNote()).append("\n");
        }
        return result("island_detail", sb.toString().trim(), island.getSourceUrls());
    }

    private StardewGuideResult islandListAnswer(String query, Optional<StardewData.IslandGuide> preferred) {
        List<StardewData.IslandGuide> matched = repository.islandGuides().stream()
                .filter(island -> matchesIslandQuery(query, island))
                .toList();
        if (matched.isEmpty()) {
            matched = preferred.map(List::of).orElseGet(repository::islandGuides);
        }
        if (matched.isEmpty()) {
            return result("island_available", "没找到姜岛探索资料。可以试试：姜岛怎么解锁、岛屿农场、海盗湾、美人鱼谜题、蜗牛教授。", List.of());
        }
        StringBuilder sb = new StringBuilder("姜岛地点/解锁对照：\n");
        for (StardewData.IslandGuide island : matched) {
            sb.append("- ").append(island.getName()).append("：");
            if (StringUtils.isNotBlank(island.getUnlock())) {
                sb.append(island.getUnlock());
            } else if (StringUtils.isNotBlank(island.getOverview())) {
                sb.append(island.getOverview());
            } else {
                sb.append("已记录姜岛探索资料");
            }
            if (island.getRecommendations() != null && !island.getRecommendations().isEmpty()) {
                sb.append("；建议：").append(island.getRecommendations().get(0));
            }
            sb.append("\n");
        }
        sb.append("建议：问具体地点名可以看路线、鹦鹉解锁、金核桃来源和推进顺序，例如“海盗湾怎么进”“岛屿农场先修什么”。");
        return result("island_available", sb.toString().trim(), collectIslandSources(matched));
    }

    private StardewGuideResult dungeonListAnswer(String query, Optional<StardewData.DungeonGuide> preferred) {
        List<StardewData.DungeonGuide> matched = repository.dungeonGuides().stream()
                .filter(dungeon -> matchesDungeonQuery(query, dungeon))
                .toList();
        if (matched.isEmpty()) {
            matched = preferred.map(List::of).orElseGet(repository::dungeonGuides);
        }
        if (matched.isEmpty()) {
            return result("dungeon_available", "没找到地下城/冒险地点资料。可以试试：矿井、骷髅洞穴、火山地牢、采石场矿洞。", List.of());
        }
        StringBuilder sb = new StringBuilder("地下城/冒险地点对照：\n");
        for (StardewData.DungeonGuide dungeon : matched) {
            sb.append("- ").append(dungeon.getName()).append("：")
                    .append(StringUtils.defaultIfBlank(dungeon.getUnlock(), "解锁条件未记录"));
            if (StringUtils.isNotBlank(dungeon.getFloorSummary())) {
                sb.append("；").append(dungeon.getFloorSummary());
            }
            if (dungeon.getRecommendations() != null && !dungeon.getRecommendations().isEmpty()) {
                sb.append("；建议：").append(dungeon.getRecommendations().get(0));
            }
            sb.append("\n");
        }
        sb.append("建议：问具体地点名可以看解锁、层数/结构、机制、奖励和路线，例如“骷髅洞穴100层怎么冲”“火山地牢怎么过”。");
        return result("dungeon_available", sb.toString().trim(), collectDungeonSources(matched));
    }

    private String animalProductSummary(StardewData.AnimalProduct product) {
        if (StringUtils.isBlank(product.getProcessedInto())) {
            return product.getName();
        }
        return product.getName() + "->" + product.getProcessedInto();
    }

    private void appendAnimalProducts(StringBuilder sb, List<StardewData.AnimalProduct> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        sb.append("产物：\n");
        for (StardewData.AnimalProduct product : products) {
            sb.append("- ").append(product.getName());
            if (product.getSellPrice() != null) {
                sb.append("：").append(formatGold(product.getSellPrice()));
            }
            if (StringUtils.isNotBlank(product.getProcessedInto())) {
                sb.append("；加工：").append(product.getProcessedInto());
                if (product.getProcessedSellPrice() != null) {
                    sb.append("（").append(formatGold(product.getProcessedSellPrice())).append("）");
                }
            }
            if (StringUtils.isNotBlank(product.getNote())) {
                sb.append("；").append(product.getNote());
            }
            sb.append("\n");
        }
    }

    private StardewGuideResult storyQuestAnswer(StardewData.StoryQuestGuide quest) {
        StringBuilder sb = new StringBuilder();
        sb.append(quest.getName()).append("：\n");
        if (StringUtils.isNotBlank(quest.getTrigger())) {
            sb.append("触发：").append(quest.getTrigger()).append("\n");
        }
        appendLines(sb, "需求", quest.getRequirements());
        appendLines(sb, "步骤", quest.getWalkthrough());
        appendLines(sb, "奖励", quest.getRewards());
        if (StringUtils.isNotBlank(quest.getRecommendation())) {
            sb.append("建议：").append(quest.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(quest.getNote())) {
            sb.append("提示：").append(quest.getNote()).append("\n");
        }
        return result("story_quest_detail", sb.toString().trim(), quest.getSourceUrls());
    }

    private StardewGuideResult storyQuestListAnswer(String query, Optional<StardewData.StoryQuestGuide> preferred) {
        List<StardewData.StoryQuestGuide> matched = repository.storyQuests().stream()
                .filter(quest -> matchesStoryQuestQuery(query, quest))
                .toList();
        if (matched.isEmpty()) {
            matched = preferred.map(List::of).orElseGet(repository::storyQuests);
        }
        if (matched.isEmpty()) {
            return result("story_quest_available", "没找到普通任务资料。可以试试：罗宾斧头、镇长短裤、神秘齐、海盗妻子。", List.of());
        }
        StringBuilder sb = new StringBuilder("普通任务对照：\n");
        for (StardewData.StoryQuestGuide quest : matched) {
            sb.append("- ").append(quest.getName()).append("：");
            if (StringUtils.isNotBlank(quest.getTrigger())) {
                sb.append(quest.getTrigger());
            } else {
                sb.append("触发条件未记录");
            }
            if (quest.getRequirements() != null && !quest.getRequirements().isEmpty()) {
                sb.append("；需求：").append(quest.getRequirements().get(0));
            }
            if (quest.getRewards() != null && !quest.getRewards().isEmpty()) {
                sb.append("；奖励：").append(quest.getRewards().get(0));
            }
            sb.append("\n");
        }
        sb.append("提示：这里是任务日志/剧情任务；特别订单板和齐先生核桃房任务走特别订单类目。问具体任务名可以看触发、步骤和奖励。");
        return result("story_quest_available", sb.toString().trim(), collectStoryQuestSources(matched));
    }

    private void appendLines(StringBuilder sb, String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        sb.append(title).append("：\n");
        for (String line : lines) {
            sb.append("- ").append(line).append("\n");
        }
    }

    private String firstOrDefault(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(0);
    }

    private StardewGuideResult bookAnswer(String query, List<StardewData.BookGuide> books) {
        List<StardewData.BookGuide> matched = books.stream()
                .limit(query.contains("哪些") || query.contains("列表") || query.contains("推荐") ? 8 : 4)
                .toList();
        if (matched.size() == 1) {
            return singleBookAnswer(matched.get(0));
        }
        StringBuilder sb = new StringBuilder("书籍对照：\n");
        for (StardewData.BookGuide book : matched) {
            sb.append("- ").append(book.getName()).append("：").append(book.getEffect());
            if (StringUtils.isNotBlank(book.getRepeatReading())) {
                sb.append("；重复阅读：").append(book.getRepeatReading());
            }
            if (book.getAcquisitions() != null && !book.getAcquisitions().isEmpty()) {
                sb.append("；获取：").append(book.getAcquisitions().get(0).getDetail());
            }
            sb.append("\n");
        }
        sb.append("建议：具体问某一本书名，可以返回更完整的来源和是否值得优先读。");
        return result("book_detail", sb.toString().trim(), collectBookSources(matched));
    }

    private StardewGuideResult singleBookAnswer(StardewData.BookGuide book) {
        StringBuilder sb = new StringBuilder();
        sb.append(book.getName()).append("：\n")
                .append("类型：").append(StringUtils.defaultIfBlank(book.getType(), "书籍")).append("\n")
                .append("效果：").append(book.getEffect()).append("\n");
        if (StringUtils.isNotBlank(book.getRepeatReading())) {
            sb.append("重复阅读：").append(book.getRepeatReading()).append("\n");
        }
        if (book.getAcquisitions() != null && !book.getAcquisitions().isEmpty()) {
            sb.append("获取：\n");
            for (StardewData.Acquisition acquisition : book.getAcquisitions()) {
                sb.append("- ").append(acquisition.getType()).append("：").append(acquisition.getDetail()).append("\n");
            }
        }
        if (StringUtils.isNotBlank(book.getRecommendation())) {
            sb.append("建议：").append(book.getRecommendation()).append("\n");
        }
        return result("book_detail", sb.toString().trim(), book.getSourceUrls());
    }

    private StardewGuideResult fishListAnswer(String query) {
        QueryContext ctx = parseContext(query);
        List<StardewData.Fish> matched = repository.fish().stream()
                .filter(f -> matchesSeason(f.getSeasons(), ctx.season))
                .filter(f -> matchesWeather(f.getWeather(), ctx.weather))
                .filter(f -> matchesLocation(f.getLocations(), ctx.location))
                .filter(f -> ctx.timeMinutes == null || matchesAnyWindow(f.getTimeWindows(), ctx.timeMinutes))
                .sorted(Comparator.comparing(StardewData.Fish::getName))
                .toList();

        if (matched.isEmpty()) {
            return result("fish_available", "按当前条件没匹配到鱼。建议补充或放宽：季节、地点、天气、时间。", List.of());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("按条件");
        if (ctx.season != null) sb.append(" ").append(ctx.season);
        if (ctx.location != null) sb.append(" ").append(ctx.location);
        if (ctx.weather != null) sb.append(" ").append(ctx.weather);
        if (ctx.timeText != null) sb.append(" ").append(ctx.timeText);
        sb.append(" 可钓：\n");

        int limit = Math.min(50, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.Fish f = matched.get(i);
            sb.append(i + 1).append(". ").append(f.getName())
                    .append("：").append(String.join("/", f.getLocations()))
                    .append("，").append(formatTimeWindows(f.getTimeWindows()))
                    .append("，").append(formatList(f.getWeather(), "任意天气"));
            if (f.getUsedIn() != null && !f.getUsedIn().isEmpty()) {
                sb.append("，用于").append(String.join("、", f.getUsedIn()));
            }
            if (StringUtils.isNotBlank(f.getNote())) {
                sb.append("，提示：").append(f.getNote());
            }
            sb.append("\n");
        }
        if (matched.size() > limit) {
            sb.append("还有 ").append(matched.size() - limit).append(" 条结果，可加地点/天气/时间缩小范围。\n");
        }
        sb.append("建议：补社区中心时优先看“用于收集包”的鱼；时间窗口短、限天气的鱼先抓。");
        return result("fish_available", sb.toString(), collectFishSources(matched));
    }

    private StardewGuideResult specificFishAnswer(String query, StardewData.Fish f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.getName()).append("：\n")
                .append("地点：").append(String.join("、", f.getLocations())).append("\n")
                .append("季节：").append(formatList(f.getSeasons(), "任意季节")).append("\n")
                .append("时间：").append(formatTimeWindows(f.getTimeWindows())).append("\n")
                .append("天气：").append(formatList(f.getWeather(), "任意天气")).append("\n");
        if (f.getUsedIn() != null && !f.getUsedIn().isEmpty()) {
            sb.append("用途：").append(String.join("、", f.getUsedIn())).append("\n");
        }
        if (StringUtils.isNotBlank(f.getNote())) {
            sb.append("提示：").append(f.getNote()).append("\n");
        }
        return result("fish_detail", sb.toString().trim(), f.getSourceUrls());
    }

    private StardewGuideResult bundleAnswer(String query, StardewData.Bundle b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b.getName()).append("（").append(b.getRoom()).append("）需要：\n");
        for (int i = 0; i < b.getItems().size(); i++) {
            StardewData.BundleItem item = b.getItems().get(i);
            sb.append(i + 1).append(". ").append(item.getName());
            if (item.getQuantity() != null && item.getQuantity() > 1) {
                sb.append(" x").append(item.getQuantity());
            }
            if (StringUtils.isNotBlank(item.getQuality())) {
                sb.append("（").append(item.getQuality()).append("）");
            }
            if (StringUtils.isNotBlank(item.getHint())) {
                sb.append("：").append(item.getHint());
            }
            sb.append("\n");
        }
        if (StringUtils.isNotBlank(b.getReward())) {
            sb.append("奖励：").append(b.getReward()).append("\n");
        }
        sb.append("建议：没有指定品质时任意品质都可交；限季节物品最好提前留一份。");
        return result("bundle", sb.toString(), b.getSourceUrls());
    }

    private StardewGuideResult cropListAnswer(String query) {
        QueryContext ctx = parseContext(query);
        List<StardewData.Crop> matched = repository.crops().stream()
                .filter(c -> matchesSeason(c.getSeasons(), ctx.season))
                .sorted(Comparator.comparing((StardewData.Crop c) -> c.getGoldPerDay() == null ? 0d : c.getGoldPerDay()).reversed()
                        .thenComparing(StardewData.Crop::getName))
                .toList();

        if (matched.isEmpty()) {
            return result("crop_available", "按当前条件没匹配到作物。建议补充或放宽：季节、具体作物名、收益/收集包用途。", List.of());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("按条件");
        if (ctx.season != null) sb.append(" ").append(ctx.season);
        sb.append(" 可种作物：\n");

        int limit = Math.min(30, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.Crop c = matched.get(i);
            sb.append(i + 1).append(". ").append(c.getName())
                    .append("：").append(formatList(c.getSeasons(), "任意季节"))
                    .append("，").append(c.getGrowDays()).append(" 天成熟");
            if (c.getRegrowDays() != null) {
                sb.append("，之后每 ").append(c.getRegrowDays()).append(" 天再收");
            }
            sb.append("，种子").append(c.getSeedPrice() == null ? "非固定商店价" : c.getSeedPrice() + "g")
                    .append("，基础售价").append(c.getSellPrice()).append("g");
            if (c.getGoldPerDay() != null) {
                sb.append("，约 ").append(String.format(Locale.ROOT, "%.2f", c.getGoldPerDay())).append("g/天");
            }
            if (c.getUsedIn() != null && !c.getUsedIn().isEmpty()) {
                sb.append("，用途：").append(String.join("、", c.getUsedIn()));
            }
            if (StringUtils.isNotBlank(c.getNote())) {
                sb.append("，提示：").append(c.getNote());
            }
            sb.append("\n");
        }
        if (matched.size() > limit) {
            sb.append("还有 ").append(matched.size() - limit).append(" 条结果，可加具体季节或作物名缩小范围。\n");
        }
        sb.append("建议：先留收集包和任务物品；单季赚钱看高 g/天，温室/姜岛优先多年生或高价值作物。");
        return result("crop_available", sb.toString(), collectCropSources(matched));
    }

    private StardewGuideResult cropDetailAnswer(StardewData.Crop c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName()).append("：\n")
                .append("季节：").append(formatList(c.getSeasons(), "任意季节")).append("\n")
                .append("种子：").append(StringUtils.defaultIfBlank(c.getSeedName(), "暂未记录")).append("，")
                .append(c.getSeedPrice() == null ? "价格非固定商店价" : c.getSeedPrice() + "g").append("\n")
                .append("来源：").append(formatList(c.getSeedSources(), "暂未记录")).append("\n")
                .append("成熟：").append(c.getGrowDays()).append(" 天");
        if (c.getRegrowDays() != null) {
            sb.append("，成熟后每 ").append(c.getRegrowDays()).append(" 天再收");
        }
        sb.append("\n")
                .append("基础售价：").append(c.getSellPrice()).append("g");
        if (c.getGoldPerDay() != null) {
            sb.append("，约 ").append(String.format(Locale.ROOT, "%.2f", c.getGoldPerDay())).append("g/天");
        }
        sb.append("\n");
        if (c.getUsedIn() != null && !c.getUsedIn().isEmpty()) {
            sb.append("用途：").append(String.join("、", c.getUsedIn())).append("\n");
        }
        if (c.getTags() != null && !c.getTags().isEmpty()) {
            sb.append("标签：").append(String.join("、", c.getTags())).append("\n");
        }
        if (StringUtils.isNotBlank(c.getNote())) {
            sb.append("提示：").append(c.getNote()).append("\n");
        }
        return result("crop_detail", sb.toString().trim(), c.getSourceUrls());
    }

    private StardewGuideResult buildingListAnswer(String query) {
        String category = parseBuildingCategory(query);
        List<StardewData.Building> matched = repository.buildings().stream()
                .filter(b -> category == null || category.equals(b.getCategory()))
                .sorted(Comparator.comparing((StardewData.Building b) -> b.getCost() == null ? Integer.MAX_VALUE : b.getCost())
                        .thenComparing(StardewData.Building::getName))
                .toList();

        if (matched.isEmpty()) {
            return result("building_available", "按当前条件没匹配到农场建筑。可以试试：鸡舍、畜棚、筒仓、鱼塘、马厩、房屋升级。", List.of());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可建/可升级建筑");
        if (category != null) {
            sb.append("（").append(formatBuildingCategory(category)).append("）");
        }
        sb.append("：\n");

        int limit = Math.min(30, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.Building b = matched.get(i);
            sb.append(i + 1).append(". ").append(b.getName())
                    .append("：").append(formatGold(b.getCost()))
                    .append("，材料：").append(formatMaterials(b.getMaterials()));
            if (StringUtils.isNotBlank(b.getPrerequisite())) {
                sb.append("，前置：").append(b.getPrerequisite());
            }
            if (b.getUnlocks() != null && !b.getUnlocks().isEmpty()) {
                sb.append("，解锁：").append(String.join("、", b.getUnlocks()));
            }
            if (StringUtils.isNotBlank(b.getNote())) {
                sb.append("，提示：").append(b.getNote());
            }
            sb.append("\n");
        }
        if (matched.size() > limit) {
            sb.append("还有 ").append(matched.size() - limit).append(" 条结果，可加具体建筑名缩小范围。\n");
        }
        sb.append("建议：前期动物路线先筒仓再鸡舍/畜棚；想解锁猪要走到豪华畜棚。");
        return result("building_available", sb.toString(), collectBuildingSources(matched));
    }

    private StardewGuideResult buildingDetailAnswer(StardewData.Building b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b.getName()).append("：\n")
                .append("花费：").append(formatGold(b.getCost())).append("\n")
                .append("材料：").append(formatMaterials(b.getMaterials())).append("\n");
        if (StringUtils.isNotBlank(b.getBuildTime())) {
            sb.append("建造/升级时间：").append(b.getBuildTime()).append("\n");
        }
        if (StringUtils.isNotBlank(b.getSize())) {
            sb.append("尺寸：").append(b.getSize()).append("\n");
        }
        if (StringUtils.isNotBlank(b.getPrerequisite())) {
            sb.append("前置：").append(b.getPrerequisite()).append("\n");
        }
        if (b.getUnlocks() != null && !b.getUnlocks().isEmpty()) {
            sb.append("解锁：").append(String.join("、", b.getUnlocks())).append("\n");
        }
        if (b.getHouses() != null && !b.getHouses().isEmpty()) {
            sb.append("可容纳/用途：").append(String.join("、", b.getHouses())).append("\n");
        }
        if (StringUtils.isNotBlank(b.getRecommendation())) {
            sb.append("建议：").append(b.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(b.getNote())) {
            sb.append("提示：").append(b.getNote()).append("\n");
        }
        return result("building_detail", sb.toString().trim(), b.getSourceUrls());
    }

    private StardewGuideResult toolListAnswer() {
        StringBuilder sb = new StringBuilder();
        sb.append("工具获取/升级总览：\n");
        for (StardewData.Tool tool : repository.tools()) {
            sb.append("- ").append(tool.getName()).append("：");
            if ("purchase".equals(tool.getCategory()) || "progression".equals(tool.getCategory())) {
                sb.append("购买/解锁线，");
            } else {
                sb.append(StringUtils.defaultIfBlank(tool.getUpgradeLocation(), "升级地点未记录")).append("，");
            }
            List<String> upgrades = tool.getUpgrades().stream()
                    .map(upgrade -> upgrade.getName() + " " + formatGold(upgrade.getCost())
                            + (upgrade.getMaterials() == null || upgrade.getMaterials().isEmpty()
                            ? "" : " + " + formatMaterials(upgrade.getMaterials())))
                    .toList();
            sb.append(String.join("；", upgrades)).append("\n");
        }
        sb.append("建议：斧头优先钢斧进秘密森林；喷壶看天气预报升级；镐子服务下矿效率；背包尽早到 24 格；垃圾桶最后考虑。");
        return result("tool_list", sb.toString(), collectToolSources(repository.tools()));
    }

    private StardewGuideResult toolDetailAnswer(String query, StardewData.Tool tool) {
        Optional<StardewData.ToolUpgrade> selected = selectToolUpgrade(query, tool);
        StringBuilder sb = new StringBuilder();
        sb.append(tool.getName()).append("：\n");
        if (StringUtils.isNotBlank(tool.getUpgradeLocation())) {
            sb.append("地点：").append(tool.getUpgradeLocation()).append("\n");
        }
        if (StringUtils.isNotBlank(tool.getUpgradeTime())) {
            sb.append("耗时：").append(tool.getUpgradeTime()).append("\n");
        }
        if (selected.isPresent()) {
            StardewData.ToolUpgrade upgrade = selected.get();
            sb.append(upgrade.getName()).append("需要：")
                    .append(formatGold(upgrade.getCost()));
            if (upgrade.getMaterials() != null && !upgrade.getMaterials().isEmpty()) {
                sb.append(" + ").append(formatMaterials(upgrade.getMaterials()));
            }
            sb.append("\n");
            if (StringUtils.isNotBlank(upgrade.getPrerequisite())) {
                sb.append("前置：").append(upgrade.getPrerequisite()).append("\n");
            }
            if (StringUtils.isNotBlank(upgrade.getEffect())) {
                sb.append("效果：").append(upgrade.getEffect()).append("\n");
            }
        } else {
            sb.append("升级/购买档位：\n");
            for (StardewData.ToolUpgrade upgrade : tool.getUpgrades()) {
                sb.append("- ").append(upgrade.getName()).append("：").append(formatGold(upgrade.getCost()));
                if (upgrade.getMaterials() != null && !upgrade.getMaterials().isEmpty()) {
                    sb.append(" + ").append(formatMaterials(upgrade.getMaterials()));
                }
                if (StringUtils.isNotBlank(upgrade.getEffect())) {
                    sb.append("，").append(upgrade.getEffect());
                }
                sb.append("\n");
            }
        }
        if (StringUtils.isNotBlank(tool.getRecommendation())) {
            sb.append("建议：").append(tool.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(tool.getNote())) {
            sb.append("提示：").append(tool.getNote()).append("\n");
        }
        return result("tool_detail", sb.toString().trim(), tool.getSourceUrls());
    }

    private Optional<StardewData.ToolUpgrade> selectToolUpgrade(String query, StardewData.Tool tool) {
        String q = StardewKnowledgeRepository.normalize(query);
        return tool.getUpgrades().stream()
                .filter(upgrade -> containsNormalized(q, upgrade.getName())
                        || containsNormalized(q, upgrade.getLevel() + "级"))
                .max(Comparator.comparingInt(upgrade -> StringUtils.length(StardewKnowledgeRepository.normalize(upgrade.getName()))));
    }

    private StardewGuideResult machineListAnswer(String query) {
        String category = parseMachineCategory(query);
        List<StardewData.Machine> matched = repository.machines().stream()
                .filter(m -> category == null || category.equals(m.getCategory()))
                .sorted(Comparator.comparing(StardewData.Machine::getName))
                .toList();

        if (matched.isEmpty()) {
            return result("machine_available", "按当前条件没匹配到机器。可以试试：小桶、罐头瓶、蛋黄酱机、鱼熏机、脱水机、诱饵制造机。", List.of());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可查询机器");
        if (category != null) {
            sb.append("（").append(formatMachineCategory(category)).append("）");
        }
        sb.append("：\n");
        int limit = Math.min(40, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.Machine m = matched.get(i);
            sb.append(i + 1).append(". ").append(m.getName())
                    .append("：配方来源：").append(StringUtils.defaultIfBlank(m.getRecipeSource(), "暂未记录"))
                    .append("，材料：").append(formatMaterials(m.getMaterials()));
            if (m.getOutputs() != null && !m.getOutputs().isEmpty()) {
                sb.append("，产出：").append(String.join("、", m.getOutputs()));
            }
            if (StringUtils.isNotBlank(m.getRecommendation())) {
                sb.append("，建议：").append(m.getRecommendation());
            }
            sb.append("\n");
        }
        sb.append("建议：赚钱优先看小桶/罐头瓶/动物加工；资源循环优先看熔炉、回收机、避雷针和宝石复制机。");
        return result("machine_available", sb.toString(), collectMachineSources(matched));
    }

    private StardewGuideResult craftingListAnswer(String query) {
        String category = parseMachineCategory(query);
        List<StardewData.CraftingRecipe> matched = repository.craftingRecipes().stream()
                .filter(recipe -> category == null || category.equals(recipe.getCategory()))
                .sorted(Comparator.comparing(StardewData.CraftingRecipe::getName))
                .toList();

        if (matched.isEmpty()) {
            return result("crafting_available", "按当前条件没匹配到制作配方。可以试试：木栅栏、茶苗、树液采集器、鱼熏机、迷你锻造台。", List.of());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可查询制作配方");
        if (category != null) {
            sb.append("（").append(formatMachineCategory(category)).append("）");
        }
        sb.append("：\n");
        int limit = Math.min(60, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.CraftingRecipe recipe = matched.get(i);
            sb.append(i + 1).append(". ").append(recipe.getName())
                    .append("：配方来源：").append(StringUtils.defaultIfBlank(recipe.getRecipeSource(), "暂未记录"))
                    .append("，材料：").append(formatMaterials(recipe.getMaterials()));
            if (recipe.getOutputs() != null && !recipe.getOutputs().isEmpty()) {
                sb.append("，产出/用途：").append(String.join("、", recipe.getOutputs()));
            }
            if (StringUtils.isNotBlank(recipe.getRecommendation())) {
                sb.append("，建议：").append(recipe.getRecommendation());
            }
            sb.append("\n");
        }
        if (matched.size() > limit) {
            sb.append("还有 ").append(matched.size() - limit).append(" 个配方未展开；可以问具体名字看材料和来源。\n");
        }
        sb.append("建议：问具体名字最稳，例如“茶苗怎么做”“树液采集器材料”“迷你锻造台怎么做”。");
        return result("crafting_available", sb.toString(), collectMachineSources(matched));
    }

    private StardewGuideResult craftingDetailAnswer(StardewData.CraftingRecipe recipe) {
        StardewGuideResult detail = machineDetailAnswer(recipe);
        return result("crafting_detail", detail.getAnswer(), detail.getSourceUrls());
    }

    private StardewGuideResult machineDetailAnswer(StardewData.Machine machine) {
        StringBuilder sb = new StringBuilder();
        sb.append(machine.getName()).append("：\n")
                .append("配方来源：").append(StringUtils.defaultIfBlank(machine.getRecipeSource(), "暂未记录")).append("\n")
                .append("材料：").append(formatMaterials(machine.getMaterials())).append("\n");
        if (machine.getInputs() != null && !machine.getInputs().isEmpty()) {
            sb.append("输入：").append(String.join("、", machine.getInputs())).append("\n");
        }
        if (machine.getOutputs() != null && !machine.getOutputs().isEmpty()) {
            sb.append("产出：").append(String.join("、", machine.getOutputs())).append("\n");
        }
        if (StringUtils.isNotBlank(machine.getProcessingTime())) {
            sb.append("耗时：").append(machine.getProcessingTime()).append("\n");
        }
        if (StringUtils.isNotBlank(machine.getFormula())) {
            sb.append("规则：").append(machine.getFormula()).append("\n");
        }
        if (StringUtils.isNotBlank(machine.getRecommendation())) {
            sb.append("建议：").append(machine.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(machine.getNote())) {
            sb.append("提示：").append(machine.getNote()).append("\n");
        }
        return result("machine_detail", sb.toString().trim(), machine.getSourceUrls());
    }

    private StardewGuideResult villagerAnswer(String query, StardewData.Villager v) {
        QueryContext ctx = parseContext(query);
        if (ctx.timeMinutes == null) {
            return result("villager_schedule", v.getName() + "住在：" + v.getLivesAt() + "\n"
                    + "要判断“现在在哪”，请至少补充游戏内时间；有季节、日期、星期、天气会更准。例如：星露谷 居民 "
                    + v.getName() + " 夏季 12日 15:00 晴天。\n"
                    + "如果遇到雨天、节日、姜岛度假、好感条件，日程会覆盖普通日程。", v.getSourceUrls());
        }

        List<StardewData.ScheduleRule> candidates = v.getSchedules().stream()
                .filter(rule -> matchesCondition(rule.getCondition(), ctx))
                .sorted(Comparator.comparing(StardewData.ScheduleRule::getPriority).reversed())
                .toList();
        if (candidates.isEmpty()) {
            candidates = v.getSchedules().stream()
                    .filter(rule -> rule.getCondition() == null || isEmptyCondition(rule.getCondition()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return villagerBestEffortScheduleAnswer(query, v, ctx);
        }

        StardewData.ScheduleRule rule = candidates.get(0);
        StardewData.ScheduleEvent current = resolveEvent(rule.getEvents(), ctx.timeMinutes);
        StringBuilder sb = new StringBuilder();
        sb.append("按：").append(ctx.season);
        if (ctx.day != null) sb.append(ctx.day).append("日");
        if (ctx.weekday != null) sb.append(" ").append(ctx.weekday);
        if (ctx.weather != null) sb.append(" ").append(ctx.weather);
        sb.append(" ").append(ctx.timeText).append("\n");
        if (current != null) {
            sb.append(v.getName()).append("大概率在：").append(current.getLocation()).append("\n");
        } else {
            sb.append("没有匹配到这个时间点的精确位置。\n");
        }
        sb.append("命中的日程：").append(rule.getLabel()).append("\n");
        if (StringUtils.isNotBlank(rule.getNote())) {
            sb.append("注意：").append(rule.getNote()).append("\n");
        } else {
            sb.append("注意：节日、剧情、姜岛度假和好感条件可能覆盖这个结果。\n");
        }
        sb.append("日程片段：");
        for (StardewData.ScheduleEvent event : rule.getEvents()) {
            sb.append("\n").append(event.getTime()).append(" ").append(event.getLocation());
        }
        return result("villager_schedule", sb.toString(), v.getSourceUrls());
    }

    private StardewGuideResult villagerBestEffortScheduleAnswer(String query, StardewData.Villager v, QueryContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getName()).append("的精确分时日程还没结构化到本地库。\n");
        sb.append("优先可找位置：").append(StringUtils.defaultIfBlank(v.getLivesAt(), "住址暂未记录")).append("\n");
        if (StringUtils.isNotBlank(v.getBirthday())) {
            sb.append("生日：").append(v.getBirthday()).append("\n");
        }
        if (ctx.season != null || ctx.day != null || ctx.weekday != null || ctx.timeText != null || ctx.weather != null) {
            sb.append("你给的条件：");
            if (ctx.season != null) sb.append(ctx.season);
            if (ctx.day != null) sb.append(ctx.day).append("日");
            if (ctx.weekday != null) sb.append(" ").append(ctx.weekday);
            if (ctx.weather != null) sb.append(" ").append(ctx.weather);
            if (ctx.timeText != null) sb.append(" ").append(ctx.timeText);
            sb.append("\n");
        }
        sb.append("建议找法：白天先去住址/工作地点，傍晚再查酒吧、镇中心或回家路线；")
                .append("雨天、节日、姜岛度假和好感剧情会覆盖普通日程。\n");
        if (v.getLovedGifts() != null && !v.getLovedGifts().isEmpty()) {
            sb.append("如果只是要顺路送礼，最爱可带：")
                    .append(String.join("、", v.getLovedGifts().stream().limit(5).toList()))
                    .append("。");
        }
        return result("villager_schedule", sb.toString().trim(), v.getSourceUrls());
    }

    private StardewGuideResult villagerProfileAnswer(StardewData.Villager v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getName()).append("：\n");
        sb.append("住址：").append(StringUtils.defaultIfBlank(v.getLivesAt(), "暂未记录")).append("\n");
        if (StringUtils.isNotBlank(v.getBirthday())) {
            sb.append("生日：").append(v.getBirthday()).append("\n");
        }
        if (v.getLovedGifts() != null && !v.getLovedGifts().isEmpty()) {
            sb.append("最爱礼物：").append(String.join("、", v.getLovedGifts())).append("\n");
        }
        if (v.getLikedGifts() != null && !v.getLikedGifts().isEmpty()) {
            sb.append("喜欢礼物：").append(String.join("、", v.getLikedGifts())).append("\n");
        }
        if (StringUtils.isNotBlank(v.getGiftNote())) {
            sb.append("送礼提示：").append(v.getGiftNote()).append("\n");
        } else {
            sb.append("送礼提示：生日送礼好感加成更高；不确定时避开讨厌物，优先送最爱礼物。\n");
        }
        sb.append("查位置可以继续问：星露谷 ").append(v.getName()).append(" 夏季 12日 15:00 晴天在哪");
        return result("villager_profile", sb.toString(), v.getSourceUrls());
    }

    private StardewGuideResult resourceAnswer(String query, StardewData.ResourceGuide r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.getName()).append("获取方式：\n");
        for (StardewData.Acquisition a : r.getAcquisitions()) {
            sb.append("- ").append(a.getType()).append("：").append(a.getDetail()).append("\n");
        }
        if (StringUtils.isNotBlank(r.getRecommendation())) {
            sb.append("推荐：").append(r.getRecommendation()).append("\n");
        }
        if (r.getUsedIn() != null && !r.getUsedIn().isEmpty()) {
            sb.append("常见用途：").append(String.join("、", r.getUsedIn())).append("\n");
        }
        return result("resource", sb.toString().trim(), r.getSourceUrls());
    }

    private StardewGuideResult monsterDropAnswer(StardewData.MonsterDropGuide monster) {
        StringBuilder sb = new StringBuilder();
        sb.append(monster.getName()).append("掉落表：\n");
        if (monster.getLocations() != null && !monster.getLocations().isEmpty()) {
            sb.append("出现地点：").append(String.join("、", monster.getLocations())).append("\n");
        }
        if (StringUtils.isNotBlank(monster.getFloors())) {
            sb.append("楼层/条件：").append(monster.getFloors()).append("\n");
        }
        if (StringUtils.isNotBlank(monster.getXp())) {
            sb.append("战斗经验：").append(monster.getXp()).append("\n");
        }
        if (monster.getDrops() != null && !monster.getDrops().isEmpty()) {
            sb.append("掉落：\n");
            for (String drop : monster.getDrops()) {
                sb.append("- ").append(drop).append("\n");
            }
        }
        if (StringUtils.isNotBlank(monster.getRecommendation())) {
            sb.append("建议：").append(monster.getRecommendation()).append("\n");
        }
        return result("monster_drop", sb.toString().trim(), monster.getSourceUrls());
    }

    private StardewGuideResult fishPondDetailAnswer(StardewData.FishPondGuide fishPond) {
        StringBuilder sb = new StringBuilder();
        sb.append(fishPond.getFishName()).append("鱼塘：\n")
                .append("容量：初始 ").append(fishPond.getInitialCapacity() == null ? "?" : fishPond.getInitialCapacity())
                .append("，上限 ").append(fishPond.getMaxCapacity() == null ? "?" : fishPond.getMaxCapacity()).append("\n");
        if (StringUtils.isNotBlank(fishPond.getSpawnFrequency())) {
            sb.append("自然增殖：").append(fishPond.getSpawnFrequency()).append("\n");
        }
        if (fishPond.getQuests() != null && !fishPond.getQuests().isEmpty()) {
            sb.append("扩容任务：\n");
            for (StardewData.FishPondQuest quest : fishPond.getQuests()) {
                sb.append("- 人口 ").append(quest.getPopulation()).append("：").append(quest.getItemsRequired()).append("\n");
            }
        }
        if (fishPond.getProducts() != null && !fishPond.getProducts().isEmpty()) {
            sb.append("产物：\n");
            for (StardewData.FishPondProduct product : fishPond.getProducts()) {
                sb.append("- 人口 ").append(product.getRequiredPopulation()).append("：").append(product.getItem());
                if (StringUtils.isNotBlank(product.getItemChance())) {
                    sb.append("，产物池概率 ").append(product.getItemChance());
                }
                if (StringUtils.isNotBlank(product.getDailyChance())) {
                    sb.append("，每日约 ").append(product.getDailyChance());
                }
                sb.append("\n");
            }
        }
        if (StringUtils.isNotBlank(fishPond.getRecommendation())) {
            sb.append("建议：").append(fishPond.getRecommendation()).append("\n");
        }
        return result("fish_pond_detail", sb.toString().trim(), fishPond.getSourceUrls());
    }

    private StardewGuideResult fishPondListAnswer(String query) {
        List<StardewData.FishPondGuide> fishPonds = repository.fishPonds().stream()
                .sorted(Comparator.comparingInt(this::fishPondPriority).thenComparing(StardewData.FishPondGuide::getFishName))
                .toList();
        if (fishPonds.isEmpty()) {
            return result("fish_pond_available", "暂时没有鱼塘产物数据。可以具体问：鲟鱼鱼塘产什么、岩浆鳗鱼鱼塘要什么。", List.of());
        }
        StringBuilder sb = new StringBuilder("鱼塘产物与推荐：\n");
        sb.append("优先考虑：鲟鱼做鱼籽酱线；岩浆鳗鱼/冰柱鱼/水滴鱼偏高价值；黄貂鱼补龙牙/电池组；午夜鱿鱼补鱿鱼墨汁。\n");
        int limit = Math.min(80, fishPonds.size());
        for (int i = 0; i < limit; i++) {
            StardewData.FishPondGuide pond = fishPonds.get(i);
            sb.append(i + 1).append(". ").append(pond.getFishName())
                    .append("：").append(primaryFishPondProducts(pond));
            if (StringUtils.isNotBlank(pond.getSpawnFrequency())) {
                sb.append("，增殖 ").append(pond.getSpawnFrequency());
            }
            if (pond.getInitialCapacity() != null && pond.getInitialCapacity() != 3) {
                sb.append("，初始容量 ").append(pond.getInitialCapacity());
            }
            sb.append("\n");
        }
        if (fishPonds.size() > limit) {
            sb.append("还有 ").append(fishPonds.size() - limit).append(" 条结果，可问具体鱼名查看扩容任务和完整概率。\n");
        }
        sb.append("建议：具体问“某鱼鱼塘产什么/要什么任务物品”，会返回完整扩容任务和产物概率。");
        return result("fish_pond_available", sb.toString(), collectFishPondSources(fishPonds));
    }

    private int fishPondPriority(StardewData.FishPondGuide fishPond) {
        String name = StringUtils.defaultString(fishPond.getFishName());
        if (List.of("鲟鱼", "岩浆鳗鱼", "冰柱鱼", "水滴鱼", "黄貂鱼", "午夜鱿鱼", "史莱姆鱼", "虚空鲑鱼").contains(name)) {
            return 0;
        }
        if (StringUtils.defaultString(fishPond.getRecommendation()).contains("优先")) {
            return 1;
        }
        return 2;
    }

    private String primaryFishPondProducts(StardewData.FishPondGuide fishPond) {
        if (fishPond.getProducts() == null || fishPond.getProducts().isEmpty()) {
            return "暂无产物记录";
        }
        return fishPond.getProducts().stream()
                .limit(4)
                .map(StardewData.FishPondProduct::getItem)
                .distinct()
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private StardewGuideResult cookingRecipeAnswer(StardewData.CookingRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        sb.append(recipe.getName()).append("：\n")
                .append("材料：").append(formatMaterials(recipe.getIngredients())).append("\n")
                .append("配方来源：").append(StringUtils.defaultIfBlank(recipe.getRecipeSource(), "暂未记录")).append("\n");
        if (StringUtils.isNotBlank(recipe.getEffect())) {
            sb.append("效果：").append(recipe.getEffect()).append("\n");
        }
        if (recipe.getBuffs() != null && !recipe.getBuffs().isEmpty()) {
            sb.append("增益：").append(formatFoodBuffs(recipe.getBuffs())).append("\n");
        }
        if (recipe.getTags() != null && !recipe.getTags().isEmpty()) {
            sb.append("适合：").append(String.join("、", recipe.getTags())).append("\n");
        }
        if (StringUtils.isNotBlank(recipe.getRecommendation())) {
            sb.append("建议：").append(recipe.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(recipe.getNote())) {
            sb.append("提示：").append(recipe.getNote()).append("\n");
        }
        return result("cooking_recipe", sb.toString().trim(), recipe.getSourceUrls());
    }

    private StardewGuideResult cookingListAnswer(String query, Optional<StardewData.CookingRecipe> directMatch) {
        List<StardewData.CookingRecipe> matched = repository.cookingRecipes().stream()
                .filter(recipe -> matchesCookingContext(recipe, query))
                .toList();
        if (matched.isEmpty() && directMatch.isPresent()) {
            matched = List.of(directMatch.get());
        }
        if (matched.isEmpty()) {
            matched = repository.cookingRecipes();
        }
        if (matched.isEmpty()) {
            return wikiFallbackAnswer(query, "本地还没有结构化料理数据。可以试试：香辣鳗鱼、幸运午餐、海泡布丁、蟹黄糕。");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("推荐料理/配方：\n");
        int limit = Math.min(12, matched.size());
        for (int i = 0; i < limit; i++) {
            StardewData.CookingRecipe recipe = matched.get(i);
            sb.append(i + 1).append(". ").append(recipe.getName())
                    .append("：材料 ").append(formatMaterials(recipe.getIngredients()));
            if (StringUtils.isNotBlank(recipe.getEffect())) {
                sb.append("；").append(recipe.getEffect());
            }
            if (recipe.getBuffs() != null && !recipe.getBuffs().isEmpty()) {
                sb.append("；增益 ").append(formatFoodBuffs(recipe.getBuffs()));
            }
            if (StringUtils.isNotBlank(recipe.getRecipeSource())) {
                sb.append("；配方 ").append(recipe.getRecipeSource());
            }
            if (StringUtils.isNotBlank(recipe.getRecommendation())) {
                sb.append("；建议 ").append(recipe.getRecommendation());
            }
            sb.append("\n");
        }
        if (matched.size() > limit) {
            sb.append("还有 ").append(matched.size() - limit).append(" 个匹配料理，可加“运气/速度/钓鱼/战斗/菜名”缩小范围。\n");
        }
        sb.append("提示：同类食物 buff 通常会互相覆盖；饮料类速度 buff 可与食物 buff 叠加。");
        return result("cooking_available", sb.toString().trim(), collectCookingSources(matched));
    }

    private boolean matchesCookingContext(StardewData.CookingRecipe recipe, String query) {
        if (recipe == null) {
            return false;
        }
        String q = StardewKnowledgeRepository.normalize(query);
        if (containsNormalized(q, recipe.getName())
                || containsNormalized(q, recipe.getNameEn())
                || recipe.getAliases().stream().anyMatch(alias -> containsNormalized(q, alias))) {
            return true;
        }
        List<String> wanted = new ArrayList<>();
        if (query.contains("骷髅") || query.contains("头骨") || query.contains("矿洞") || query.contains("矿井")
                || query.contains("火山") || query.contains("下矿") || query.contains("冲层")) {
            wanted.addAll(List.of("mining", "skull_cavern", "luck", "speed", "defense"));
        }
        if (query.contains("钓鱼") || query.contains("鱼")) {
            wanted.add("fishing");
        }
        if (query.contains("战斗") || query.contains("打怪") || query.contains("怪物")) {
            wanted.addAll(List.of("combat", "attack", "defense"));
        }
        if (query.contains("耕种") || query.contains("种地") || query.contains("农业")) {
            wanted.add("farming");
        }
        if (query.contains("觅食") || query.contains("采集")) {
            wanted.add("foraging");
        }
        if (query.contains("速度")) {
            wanted.add("speed");
        }
        if (query.contains("运气") || query.contains("幸运")) {
            wanted.add("luck");
        }
        if (query.contains("防御")) {
            wanted.add("defense");
        }
        if (query.contains("磁力") || query.contains("吸")) {
            wanted.add("magnetism");
        }
        if (query.contains("回血") || query.contains("回复") || query.contains("补血")
                || query.contains("恢复") || query.contains("普通料理")) {
            wanted.add("healing");
        }
        if (query.contains("基础") || query.contains("早期") || query.contains("前期")) {
            wanted.add("early_game");
        }
        if (query.contains("材料菜") || query.contains("配料") || query.contains("中间材料")
                || query.contains("做其他料理")) {
            wanted.add("ingredient_dish");
        }
        if (query.contains("姜岛") || query.contains("岛上") || query.contains("热带")) {
            wanted.add("island");
        }
        if (wanted.isEmpty()) {
            return isBroadCookingQuery(query);
        }
        Set<String> tags = new LinkedHashSet<>();
        if (recipe.getTags() != null) {
            recipe.getTags().stream().map(StardewKnowledgeRepository::normalize).forEach(tags::add);
        }
        if (recipe.getBuffs() != null) {
            recipe.getBuffs().stream()
                    .map(StardewData.FoodBuff::getName)
                    .map(StardewKnowledgeRepository::normalize)
                    .forEach(tags::add);
        }
        return wanted.stream()
                .map(StardewKnowledgeRepository::normalize)
                .anyMatch(tags::contains);
    }

    private Optional<ShopStockMatch> findShopStock(String query) {
        String q = StardewKnowledgeRepository.normalize(query);
        ShopStockMatch best = null;
        for (StardewData.Shop shop : repository.shops()) {
            int shopScore = scoreText(q, shop.getName(), shop.getNameEn(), shop.getAliases());
            for (StardewData.ShopItem item : shop.getStock()) {
                int itemScore = scoreText(q, item.getName(), null, item.getAliases());
                if (itemScore <= 0) {
                    continue;
                }
                int score = itemScore + Math.max(0, shopScore / 4);
                if (best == null || score > best.score()) {
                    best = new ShopStockMatch(shop, item, score);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private StardewGuideResult shopAnswer(StardewData.Shop shop) {
        StringBuilder sb = new StringBuilder();
        sb.append(shop.getName()).append("：\n")
                .append("位置：").append(StringUtils.defaultIfBlank(shop.getLocation(), "暂未记录")).append("\n")
                .append("营业：").append(StringUtils.defaultIfBlank(shop.getOpenHours(), "暂未记录")).append("\n");
        if (StringUtils.isNotBlank(shop.getClosed())) {
            sb.append("关闭/例外：").append(shop.getClosed()).append("\n");
        }
        if (StringUtils.isNotBlank(shop.getUnlockCondition())) {
            sb.append("解锁：").append(shop.getUnlockCondition()).append("\n");
        }
        if (shop.getStock() != null && !shop.getStock().isEmpty()) {
            sb.append("常用商品/服务：\n");
            for (StardewData.ShopItem item : shop.getStock().stream().limit(12).toList()) {
                sb.append("- ").append(item.getName()).append("：").append(formatShopPrice(item));
                if (StringUtils.isNotBlank(item.getAvailability())) {
                    sb.append("，").append(item.getAvailability());
                }
                if (StringUtils.isNotBlank(item.getNote())) {
                    sb.append("，").append(item.getNote());
                }
                sb.append("\n");
            }
        }
        if (StringUtils.isNotBlank(shop.getRecommendation())) {
            sb.append("建议：").append(shop.getRecommendation()).append("\n");
        }
        if (StringUtils.isNotBlank(shop.getNote())) {
            sb.append("提示：").append(shop.getNote()).append("\n");
        }
        return result("shop", sb.toString().trim(), shop.getSourceUrls());
    }

    private StardewGuideResult shopItemAnswer(String query, ShopStockMatch match) {
        StardewData.Shop shop = match.shop();
        List<StardewData.ShopItem> related = relatedShopItems(query, shop, match.item());
        StringBuilder sb = new StringBuilder();
        sb.append("可以去：").append(shop.getName()).append("\n")
                .append("位置：").append(StringUtils.defaultIfBlank(shop.getLocation(), "暂未记录")).append("\n")
                .append("营业：").append(StringUtils.defaultIfBlank(shop.getOpenHours(), "暂未记录")).append("\n");
        if (StringUtils.isNotBlank(shop.getClosed())) {
            sb.append("关闭/例外：").append(shop.getClosed()).append("\n");
        }
        sb.append(related.size() > 1 ? "相关商品/服务：\n" : "商品/服务：\n");
        for (StardewData.ShopItem item : related) {
            sb.append("- ").append(item.getName()).append("：").append(formatShopPrice(item));
            if (StringUtils.isNotBlank(item.getAvailability())) {
                sb.append("，").append(item.getAvailability());
            }
            if (StringUtils.isNotBlank(item.getNote())) {
                sb.append("，").append(item.getNote());
            }
            sb.append("\n");
        }
        if (StringUtils.isNotBlank(shop.getRecommendation())) {
            sb.append("建议：").append(shop.getRecommendation()).append("\n");
        }
        return result("shop_item", sb.toString().trim(), shop.getSourceUrls());
    }

    private List<StardewData.ShopItem> relatedShopItems(String query, StardewData.Shop shop, StardewData.ShopItem selected) {
        if (query.contains("背包")) {
            List<StardewData.ShopItem> backpacks = shop.getStock().stream()
                    .filter(item -> containsNormalized(StardewKnowledgeRepository.normalize(item.getName()), "背包")
                            || item.getAliases().stream().anyMatch(alias -> alias.contains("背包")))
                    .toList();
            if (!backpacks.isEmpty()) {
                return backpacks;
            }
        }
        if (query.contains("书") && ("bookseller".equals(shop.getId()) || query.contains("书商"))) {
            return shop.getStock();
        }
        return List.of(selected);
    }

    private String formatShopPrice(StardewData.ShopItem item) {
        if (item.getPrice() != null) {
            if (StringUtils.isNotBlank(item.getCurrency()) && !"gold".equalsIgnoreCase(item.getCurrency())) {
                return item.getPrice().toString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",") + " " + item.getCurrency();
            }
            return formatGold(item.getPrice());
        }
        return StringUtils.defaultIfBlank(item.getCurrency(), "非金币购买/价格随库存变化");
    }

    private int scoreText(String q, String name, String en, List<String> aliases) {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.add(en);
        if (aliases != null) {
            names.addAll(aliases);
        }
        int score = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (String n : names) {
            String normalized = StardewKnowledgeRepository.normalize(n);
            if (!seen.add(normalized) || normalized.isEmpty()) {
                continue;
            }
            if (q.equals(normalized)) {
                score += 1000 + normalized.length() * 20;
            } else if (normalized.length() >= 2 && q.contains(normalized)) {
                score += normalized.length() * 20;
            } else if (q.length() >= 2 && normalized.contains(q)) {
                score += q.length() * 5;
            }
        }
        return score;
    }

    private StardewGuideResult wikiFallbackAnswer(String query, String fallbackText) {
        if (wikiClient == null) {
            return result("wiki_fallback_unavailable", fallbackText, List.of());
        }
        List<StardewWikiPage> pages = wikiClient.search(query, 3);
        if (pages == null || pages.isEmpty()) {
            return result("wiki_fallback_empty", fallbackText + "\n我暂时没找到更匹配的资料，可以换个物品名、地点或机制关键词再问一次。", List.of());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("我找到这些可能相关的内容：\n");
        for (int i = 0; i < pages.size(); i++) {
            StardewWikiPage page = pages.get(i);
            sb.append(i + 1).append(". ").append(page.getTitle()).append("\n");
            sb.append(trimExcerpt(page.getExcerpt())).append("\n");
        }
        sb.append("如果你告诉我当前季节、进度或具体目标，我可以继续帮你缩小到最该做的一步。");
        List<String> urls = pages.stream()
                .map(StardewWikiPage::getUrl)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        return result("wiki_fallback", sb.toString().trim(), urls);
    }

    private String trimExcerpt(String excerpt) {
        if (excerpt == null) {
            return "";
        }
        String normalized = excerpt.trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500) + "...";
    }

    private StardewData.ScheduleEvent resolveEvent(List<StardewData.ScheduleEvent> events, int timeMinutes) {
        StardewData.ScheduleEvent current = null;
        for (StardewData.ScheduleEvent event : events) {
            Integer eventTime = parseTimeValue(event.getTime());
            if (eventTime == null) {
                continue;
            }
            if (eventTime <= timeMinutes) {
                current = event;
            } else {
                break;
            }
        }
        return current == null && !events.isEmpty() ? events.get(0) : current;
    }

    private boolean matchesCondition(StardewData.Condition c, QueryContext ctx) {
        if (c == null || isEmptyCondition(c)) {
            return true;
        }
        if (c.getSeasons() != null && !c.getSeasons().isEmpty() && !c.getSeasons().contains(ctx.season)) {
            return false;
        }
        if (c.getDays() != null && !c.getDays().isEmpty() && (ctx.day == null || !c.getDays().contains(ctx.day))) {
            return false;
        }
        if (c.getWeekdays() != null && !c.getWeekdays().isEmpty()
                && (ctx.weekday == null || !c.getWeekdays().contains(ctx.weekday))) {
            return false;
        }
        if (c.getWeather() != null && !c.getWeather().isEmpty()
                && (ctx.weather == null || !c.getWeather().contains(ctx.weather))) {
            return false;
        }
        return true;
    }

    private boolean isEmptyCondition(StardewData.Condition c) {
        return (c.getSeasons() == null || c.getSeasons().isEmpty())
                && (c.getDays() == null || c.getDays().isEmpty())
                && (c.getWeekdays() == null || c.getWeekdays().isEmpty())
                && (c.getWeather() == null || c.getWeather().isEmpty());
    }

    private boolean matchesSeason(List<String> seasons, String season) {
        return season == null || seasons == null || seasons.isEmpty()
                || seasons.contains("任意季节") || seasons.contains(season);
    }

    private boolean matchesWeather(List<String> weather, String selected) {
        return selected == null || weather == null || weather.isEmpty()
                || weather.contains("任意天气") || weather.contains(selected);
    }

    private boolean matchesLocation(List<String> locations, String location) {
        if (location == null || locations == null || locations.isEmpty()) {
            return true;
        }
        String l = StardewKnowledgeRepository.normalize(location);
        return locations.stream().anyMatch(item -> StardewKnowledgeRepository.normalize(item).contains(l));
    }

    private boolean matchesAnyWindow(List<StardewData.TimeWindow> windows, int minutes) {
        if (windows == null || windows.isEmpty()) {
            return true;
        }
        for (StardewData.TimeWindow window : windows) {
            Integer start = parseTimeValue(window.getStart());
            Integer end = parseTimeValue(window.getEnd());
            if (start == null || end == null) {
                return true;
            }
            if (start <= end && minutes >= start && minutes <= end) {
                return true;
            }
            if (start > end && (minutes >= start || minutes <= end)) {
                return true;
            }
        }
        return false;
    }

    private QueryContext parseContext(String query) {
        QueryContext ctx = new QueryContext();
        ctx.season = parseSeason(query);
        ctx.weather = parseWeather(query);
        ctx.location = parseLocation(query);
        ctx.weekday = parseWeekday(query);

        Matcher dayMatcher = DAY.matcher(query);
        if (dayMatcher.find()) {
            if (ctx.season == null) {
                ctx.season = parseSeason(dayMatcher.group(1));
            }
            ctx.day = Integer.parseInt(dayMatcher.group(2));
        }

        Integer minutes = parseTimeValue(query);
        if (minutes != null) {
            ctx.timeMinutes = minutes;
            ctx.timeText = formatMinutes(minutes);
        }
        return ctx;
    }

    private String parseSeason(String query) {
        if (query == null) return null;
        if (query.contains("春季") || query.contains("春天") || query.contains("春")) return "春季";
        if (query.contains("夏季") || query.contains("夏天") || query.contains("夏")) return "夏季";
        if (query.contains("秋季") || query.contains("秋天") || query.contains("秋")) return "秋季";
        if (query.contains("冬季") || query.contains("冬天") || query.contains("冬")) return "冬季";
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("spring")) return "春季";
        if (lower.contains("summer")) return "夏季";
        if (lower.contains("fall") || lower.contains("autumn")) return "秋季";
        if (lower.contains("winter")) return "冬季";
        return null;
    }

    private String parseWeather(String query) {
        if (query.contains("雨")) return "雨天";
        if (query.contains("晴") || query.contains("太阳")) return "晴天";
        return null;
    }

    private String parseLocation(String query) {
        if (query.contains("大家族") || query.contains("大家庭") || query.contains("传说鱼二代")
                || query.contains("传说鱼 II") || query.contains("传说鱼2")) {
            return "大家族鱼";
        }
        if (query.contains("传说鱼") || query.contains("传奇鱼")) return "传说鱼";
        if (query.contains("蟹笼淡水") || query.contains("淡水蟹笼")) return "蟹笼淡水";
        if (query.contains("蟹笼海水") || query.contains("海水蟹笼")) return "蟹笼海水";
        if (query.contains("蟹笼")) return "蟹笼";
        if (query.contains("洞穴果冻") || query.contains("矿洞果冻")) return "矿井";
        if (query.contains("海果冻") || query.contains("海水果冻")) return "海洋";
        if (query.contains("河果冻") || query.contains("淡水果冻")) return "河流";
        if (query.contains("果冻")) return "果冻";
        if (query.contains("瀑布")) return "瀑布";
        if (query.contains("夜市") || query.contains("潜艇")) return "夜市潜艇";
        if (query.contains("姜岛")) return "姜岛";
        if (query.contains("海")) return "海洋";
        if (query.contains("河")) return "河流";
        if (query.contains("山湖") || query.contains("湖") || query.contains("山")) return "湖";
        if (query.contains("森林")) return "森林";
        if (query.contains("矿")) return "矿井";
        if (query.contains("沙漠")) return "沙漠";
        return null;
    }

    private String parseWeekday(String query) {
        String[] weekdays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "星期天"};
        for (String weekday : weekdays) {
            if (query.contains(weekday)) {
                return "星期天".equals(weekday) ? "星期日" : weekday;
            }
        }
        if (query.contains("周一")) return "星期一";
        if (query.contains("周二")) return "星期二";
        if (query.contains("周三")) return "星期三";
        if (query.contains("周四")) return "星期四";
        if (query.contains("周五")) return "星期五";
        if (query.contains("周六")) return "星期六";
        if (query.contains("周日") || query.contains("周天")) return "星期日";
        return null;
    }

    private Integer parseTimeValue(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = HH_MM.matcher(value);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
        }
        matcher = HOUR_ZH.matcher(value);
        if (matcher.find()) {
            String period = matcher.group(1);
            int hour = Integer.parseInt(matcher.group(2));
            if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) {
                hour += 12;
            }
            if ("中午".equals(period) && hour < 11) {
                hour += 12;
            }
            if ("凌晨".equals(period) && hour == 12) {
                hour = 0;
            }
            return hour * 60;
        }
        return null;
    }

    private String cleanQuery(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        return rawQuery
                .replaceFirst("^\\s*/?星露谷(物语)?", "")
                .replaceFirst("^\\s*/?stardew", "")
                .trim();
    }

    private boolean looksLikeScheduleQuery(String query) {
        return query.contains("在哪") || query.contains("位置") || query.contains("日程")
                || query.contains("居民") || query.contains("现在") || parseTimeValue(query) != null;
    }

    private boolean looksLikeVillagerProfileQuery(String query) {
        return query.contains("喜欢") || query.contains("最爱") || query.contains("礼物")
                || query.contains("送什么") || query.contains("生日") || query.contains("住哪")
                || query.contains("住址") || query.contains("好感");
    }

    private boolean looksLikeResourceQuery(String query) {
        return query.contains("怎么") || query.contains("获取") || query.contains("获得")
                || query.contains("哪里") || query.contains("刷") || query.contains("来源");
    }

    private boolean looksLikeMonsterDropQuery(String query) {
        return query.contains("掉什么") || query.contains("掉落表") || query.contains("掉落")
                || query.contains("战利品") || query.contains("在哪刷") || query.contains("哪里刷");
    }

    private boolean looksLikeFishPondQuery(String query) {
        return query.contains("鱼塘") || query.contains("鱼籽酱") || query.contains("鱼子酱")
                || (query.contains("鱼籽") && !query.contains("钓"));
    }

    private boolean looksLikeFishPondBuildingQuery(String query) {
        return query.contains("鱼塘")
                && containsAny(query, "建造", "建筑", "罗宾", "多少钱", "价格", "材料", "占地", "尺寸")
                && !containsAny(query, "产什么", "产出", "产物", "养什么", "放什么", "扩容", "任务", "鱼籽", "鱼子酱", "鱼籽酱");
    }

    private boolean looksLikeSpecialOrderQuery(String query) {
        if (containsAny(query, "大家族", "大家庭")
                && containsAny(query, "传说鱼", "传说之鱼", "哪些鱼", "什么鱼", "钓")) {
            return false;
        }
        if (looksLikeFishPondQuery(query) && containsAny(query, "扩容", "任务", "产什么", "产出", "产物", "鱼籽", "鱼子酱", "鱼籽酱")) {
            return false;
        }
        return containsAny(query,
                "特别订单", "特殊订单", "订单板", "特别任务", "特殊任务", "齐先生任务", "齐先生挑战",
                "核桃房任务", "核桃房订单", "罗宾资源冲刺", "罗宾的项目", "岛屿食材",
                "齐瓜", "齐豆", "齐果", "五彩农场", "五彩格兰奇", "大家族", "深处的危险",
                "骷髅洞穴入侵", "齐氏料理", "齐的善意", "四颗宝石", "饥饿挑战");
    }

    private boolean looksLikeSkillGuideQuery(String query) {
        if (looksLikeCookingQuery(query) && containsAny(query, "吃什么", "料理", "食物", "buff", "增益")) {
            return false;
        }
        if (!repository.findBooks(query).isEmpty()
                && containsAny(query, "在哪里买", "哪里买", "谁卖", "多少钱", "购买", "有什么用")) {
            return false;
        }
        if (looksLikeMonsterDropQuery(query)) {
            return false;
        }
        if (query.contains("职业") && containsAny(query, "重置", "更换", "换", "改", "洗")) {
            return true;
        }
        if (containsAny(query, "职业重置", "重置职业", "换职业", "改职业", "洗职业", "洗点", "不确定雕像")) {
            return true;
        }
        return containsAny(query, "耕种", "农业", "采矿", "挖矿", "觅食", "采集", "钓鱼", "战斗")
                && containsAny(query, "技能", "等级", "经验", "职业", "怎么练", "怎么升级", "快速升级", "等级低", "刷");
    }

    private boolean looksLikeFestivalQuery(String query) {
        return containsAny(query,
                "节日", "活动", "庆典", "赛马会", "沙漠节", "复活节", "蛋蛋节", "彩蛋节",
                "花舞节", "花舞会", "夏威夷宴会", "夏威夷", "鳟鱼大赛", "月光水母",
                "水母起舞", "星露谷展览会", "展览会", "集市", "万灵节", "万圣节",
                "冰雪节", "冰雪", "鱿鱼节", "夜市", "冬星盛宴", "冬日星盛宴",
                "Egg Festival", "Desert Festival", "Flower Dance", "Luau", "Trout Derby",
                "Dance of the Moonlight Jellies", "Stardew Valley Fair", "Spirit's Eve",
                "Festival of Ice", "SquidFest", "Night Market", "Feast of the Winter Star");
    }

    private boolean isBroadSkillGuideQuery(String query) {
        return containsAny(query, "技能有哪些", "所有技能", "技能总览", "技能攻略", "职业有哪些", "职业总览")
                && !containsAny(query, "耕种", "农业", "采矿", "挖矿", "觅食", "采集", "钓鱼", "战斗");
    }

    private boolean isBroadFestivalQuery(String query) {
        return containsAny(query, "有哪些", "列表", "总览", "全年", "所有", "日历", "什么时候有节日")
                || (containsAny(query, "春季", "春天", "春", "夏季", "夏天", "夏", "秋季", "秋天", "秋", "冬季", "冬天", "冬")
                && containsAny(query, "节日", "活动")
                && repository.findFestivalEvent(query).isEmpty());
    }

    private boolean isBroadFarmMapQuery(String query) {
        return containsAny(query, "有哪些", "列表", "总览", "所有", "全部", "怎么选", "选什么", "推荐", "适合", "新手");
    }

    private boolean isBroadFarmAnimalQuery(String query) {
        return looksLikeFarmAnimalListQuery(query);
    }

    private boolean looksLikeFarmAnimalListQuery(String query) {
        return containsAny(query, "有哪些", "列表", "总览", "所有", "全部", "养什么")
                || (containsAny(query, "推荐", "适合", "哪个好", "赚钱", "前期", "后期")
                && containsAny(query, "动物", "农场动物", "养什么", "养哪个", "哪个好"))
                || (containsAny(query, "鸡舍", "畜棚", "动物") && containsAny(query, "产物", "对照", "比较"));
    }

    private boolean looksLikeAnimalCareGuideQuery(String query) {
        return containsAny(query, "怎么养", "心情", "好感", "喂", "摸", "干草", "草地", "不产", "不出", "为什么")
                && !repository.findFarmAnimal(query).isPresent();
    }

    private boolean isBroadDungeonQuery(String query) {
        return containsAny(query, "地下城有哪些", "冒险地点有哪些", "矿洞有哪些", "矿井有哪些", "地点列表", "地下城列表", "冒险地点列表", "总览", "全部地下城")
                || (containsAny(query, "地下城", "冒险地点", "矿洞", "矿井") && containsAny(query, "哪些", "列表", "所有", "全部", "总览"));
    }

    private boolean isBroadIslandQuery(String query) {
        return containsAny(query, "姜岛有哪些", "岛上有哪些", "岛屿有哪些", "姜岛地点", "姜岛区域", "姜岛列表", "姜岛总览", "姜岛先做什么", "姜岛先解锁什么", "姜岛攻略")
                || (containsAny(query, "姜岛", "岛屿", "金核桃解锁", "鹦鹉解锁") && containsAny(query, "哪些", "列表", "所有", "全部", "总览", "顺序", "先做", "先解锁"));
    }

    private boolean isBroadStoryQuestQuery(String query) {
        return containsAny(query, "任务有哪些", "普通任务有哪些", "剧情任务有哪些", "任务列表", "普通任务列表", "剧情任务列表", "所有任务", "全部任务", "总览")
                || (containsAny(query, "普通任务", "剧情任务", "任务日志") && containsAny(query, "哪些", "列表", "总览", "所有", "全部"));
    }

    private boolean matchesStoryQuestQuery(String query, StardewData.StoryQuestGuide quest) {
        String q = StardewKnowledgeRepository.normalize(query);
        Optional<StardewData.StoryQuestGuide> direct = repository.findStoryQuest(query);
        if (direct.isPresent() && direct.get().getId().equals(quest.getId()) && !isBroadStoryQuestQuery(query)) {
            return true;
        }
        if (containsAny(q, "任务有哪些", "任务列表", "普通任务", "剧情任务", "任务日志", "所有任务", "全部任务")) {
            return true;
        }
        return containsAnyNormalized(q, quest.getAliases())
                || containsNormalized(q, quest.getName())
                || containsNormalized(q, quest.getNameEn())
                || containsNormalized(q, quest.getTrigger())
                || containsAnyNormalized(q, quest.getRequirements())
                || containsAnyNormalized(q, quest.getWalkthrough())
                || containsAnyNormalized(q, quest.getRewards())
                || containsNormalized(q, quest.getRecommendation())
                || containsNormalized(q, quest.getNote());
    }

    private boolean looksLikeSharedFarmAnimalProductQuery(String query) {
        String q = StardewKnowledgeRepository.normalize(query);
        List<StardewData.FarmAnimalGuide> productMatches = repository.farmAnimals().stream()
                .filter(animal -> containsAnimalProduct(q, animal.getProducts()))
                .toList();
        if (productMatches.size() < 2) {
            return false;
        }
        return productMatches.stream().noneMatch(animal -> containsSpecificFarmAnimalName(q, animal));
    }

    private boolean containsSpecificFarmAnimalName(String query, StardewData.FarmAnimalGuide animal) {
        return containsNormalized(query, animal.getName())
                || containsNormalized(query, animal.getNameEn());
    }

    private boolean matchesFarmMapQuery(String query, StardewData.FarmMapGuide map) {
        String q = StardewKnowledgeRepository.normalize(query);
        int directScore = repository.findFarmMap(query)
                .filter(found -> found.getId().equals(map.getId()))
                .map(found -> 100)
                .orElse(0);
        if (directScore > 0 && !isBroadFarmMapQuery(query)) {
            return true;
        }
        if (containsAny(q, "有哪些", "列表", "总览", "所有", "全部", "农场地图", "农场类型", "开局农场", "农场怎么选", "哪种农场", "哪个农场")) {
            return true;
        }
        return containsAnyNormalized(q, map.getAssociatedSkills())
                || containsAnyNormalized(q, map.getBestFor())
                || containsAnyNormalized(q, map.getAliases())
                || containsAnyNormalized(q, map.getPerks())
                || containsAnyNormalized(q, map.getLimits())
                || containsAnyNormalized(q, map.getFishing())
                || containsAnyNormalized(q, map.getRecommendations())
                || containsNormalized(q, map.getLayoutSummary())
                || containsNormalized(q, map.getNote())
                || containsAny(q, StardewKnowledgeRepository.normalize(map.getName()), StardewKnowledgeRepository.normalize(map.getNameEn()));
    }

    private boolean matchesFarmAnimalQuery(String query, StardewData.FarmAnimalGuide animal) {
        String q = StardewKnowledgeRepository.normalize(query);
        Optional<StardewData.FarmAnimalGuide> direct = repository.findFarmAnimal(query);
        if (direct.isPresent() && direct.get().getId().equals(animal.getId()) && !looksLikeFarmAnimalListQuery(query)) {
            return true;
        }
        if (containsAny(q, "有哪些", "列表", "总览", "所有", "全部", "农场动物", "动物产物", "鸡舍动物", "畜棚动物")) {
            return true;
        }
        if (containsAny(q, "鸡舍") && "coop".equals(animal.getCategory())) {
            return true;
        }
        if (containsAny(q, "畜棚", "谷仓") && "barn".equals(animal.getCategory())) {
            return true;
        }
        return containsAnyNormalized(q, animal.getAliases())
                || containsAnyNormalized(q, animal.getBestFor())
                || containsAnyNormalized(q, animal.getMechanics())
                || containsAnyNormalized(q, animal.getRecommendations())
                || containsAnimalProduct(q, animal.getProducts())
                || containsNormalized(q, animal.getName())
                || containsNormalized(q, animal.getNameEn())
                || containsNormalized(q, animal.getAcquisition())
                || containsNormalized(q, animal.getProduceFrequency())
                || containsNormalized(q, animal.getNote());
    }

    private boolean matchesDungeonQuery(String query, StardewData.DungeonGuide dungeon) {
        String q = StardewKnowledgeRepository.normalize(query);
        Optional<StardewData.DungeonGuide> direct = repository.findDungeonGuide(query);
        if (direct.isPresent() && direct.get().getId().equals(dungeon.getId()) && !isBroadDungeonQuery(query)) {
            return true;
        }
        if (containsAny(q, "地下城有哪些", "冒险地点有哪些", "矿洞有哪些", "地下城列表", "冒险地点列表", "全部地下城", "所有地下城")) {
            return true;
        }
        return containsAnyNormalized(q, dungeon.getAliases())
                || containsNormalized(q, dungeon.getName())
                || containsNormalized(q, dungeon.getNameEn())
                || containsNormalized(q, dungeon.getLocation())
                || containsNormalized(q, dungeon.getUnlock())
                || containsNormalized(q, dungeon.getFloorSummary())
                || containsAnyNormalized(q, dungeon.getMechanics())
                || containsAnyNormalized(q, dungeon.getMonsters())
                || containsAnyNormalized(q, dungeon.getLoot())
                || containsAnyNormalized(q, dungeon.getQuests())
                || containsAnyNormalized(q, dungeon.getRecommendations())
                || containsNormalized(q, dungeon.getNote());
    }

    private boolean matchesIslandQuery(String query, StardewData.IslandGuide island) {
        String q = StardewKnowledgeRepository.normalize(query);
        Optional<StardewData.IslandGuide> direct = repository.findIslandGuide(query);
        if (direct.isPresent() && direct.get().getId().equals(island.getId()) && !isBroadIslandQuery(query)) {
            return true;
        }
        if (containsAny(q, "姜岛有哪些", "岛上有哪些", "岛屿有哪些", "姜岛地点", "姜岛区域", "姜岛列表", "全部姜岛", "所有姜岛")) {
            return true;
        }
        return containsAnyNormalized(q, island.getAliases())
                || containsNormalized(q, island.getName())
                || containsNormalized(q, island.getNameEn())
                || containsNormalized(q, island.getRegion())
                || containsNormalized(q, island.getUnlock())
                || containsNormalized(q, island.getOverview())
                || containsAnyNormalized(q, island.getActivities())
                || containsAnyNormalized(q, island.getWalnuts())
                || containsAnyNormalized(q, island.getRewards())
                || containsAnyNormalized(q, island.getRecommendations())
                || containsNormalized(q, island.getNote());
    }

    private boolean containsAnimalProduct(String query, List<StardewData.AnimalProduct> products) {
        if (products == null || products.isEmpty()) {
            return false;
        }
        for (StardewData.AnimalProduct product : products) {
            if (containsNormalized(query, product.getName())
                    || containsNormalized(query, product.getProcessedInto())
                    || containsNormalized(query, product.getNote())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFishingQuestion(String query) {
        return query.contains("钓") || query.contains("什么鱼") || query.contains("哪些鱼")
                || query.contains("能抓") || query.contains("可抓") || query.contains("蟹笼");
    }

    private boolean looksLikeCropQuery(String query) {
        return query.contains("作物") || query.contains("种什么") || query.contains("种植")
                || query.contains("几天成熟") || query.contains("成熟")
                || query.contains("几天长好") || query.contains("收益")
                || query.contains("农作物") || query.contains("要不要留")
                || query.contains("适合温室") || query.contains("温室种");
    }

    private boolean looksLikeToolQuery(String query) {
        return query.contains("工具升级") || query.contains("升级工具")
                || query.contains("斧头") || query.contains("斧子")
                || query.contains("镐子") || query.contains("十字镐")
                || query.contains("喷壶") || query.contains("浇水壶") || query.contains("水壶")
                || query.contains("锄头") || query.contains("垃圾桶")
                || query.contains("鱼竿") || query.contains("钓竿")
                || query.contains("铜盘") || query.contains("淘金盘") || query.contains("淘盘")
                || query.contains("钢盘") || query.contains("金盘") || query.contains("铱盘")
                || query.contains("镰刀") || query.contains("金镰刀") || query.contains("铱金镰刀")
                || query.contains("背包") || query.contains("物品栏")
                || query.contains("奶桶") || query.contains("挤奶桶")
                || query.contains("剪刀") || query.contains("剪羊毛")
                || query.contains("铜斧") || query.contains("钢斧") || query.contains("金斧") || query.contains("铱斧")
                || query.contains("铜镐") || query.contains("钢镐") || query.contains("金镐") || query.contains("铱镐")
                || query.contains("铜锄") || query.contains("钢锄") || query.contains("金锄") || query.contains("铱锄")
                || query.contains("铜喷壶") || query.contains("钢喷壶") || query.contains("金喷壶") || query.contains("铱喷壶");
    }

    private boolean looksLikeBuildingQuery(String query) {
        return query.contains("农场建筑") || query.contains("建筑有哪些") || query.contains("建筑列表")
                || query.contains("建什么") || query.contains("建造") || query.contains("罗宾")
                || query.contains("鸡舍") || query.contains("畜棚") || query.contains("筒仓")
                || query.contains("鱼塘") || query.contains("马厩") || query.contains("史莱姆屋")
                || query.contains("史莱姆小屋") || query.contains("磨坊") || query.contains("水井")
                || query.contains("小屋") || query.contains("出货箱") || query.contains("谷仓")
                || query.contains("大鸡舍") || query.contains("豪华鸡舍") || query.contains("大畜棚")
                || query.contains("豪华畜棚") || query.contains("升级房子") || query.contains("房屋升级")
                || query.contains("方尖塔") || query.contains("方尖碑") || query.contains("黄金钟")
                || query.contains("黄金时钟") || query.contains("金钟") || query.contains("祝尼魔小屋")
                || query.contains("祝尼魔屋") || query.contains("自动收菜") || query.contains("魔法建筑")
                || query.contains("法师塔建筑") || query.contains("社区升级") || query.contains("潘姆房")
                || query.contains("潘姆房子") || query.contains("城镇捷径") || query.contains("小镇捷径")
                || query.contains("扩建房子") || query.contains("解锁猪") || query.contains("养猪")
                || query.contains("养兔") || query.contains("养鸭") || query.contains("养山羊")
                || query.contains("养绵羊") || query.contains("养牛") || query.contains("养鸡")
                || query.contains("养马");
    }

    private boolean looksLikeMachineQuery(String query) {
        return query.contains("机器") || query.contains("加工设备") || query.contains("工匠设备")
                || query.contains("怎么加工") || query.contains("加工什么") || query.contains("产出")
                || query.contains("小桶") || query.contains("酒桶") || query.contains("酿酒")
                || query.contains("罐头瓶") || query.contains("果酱") || query.contains("腌菜") || query.contains("鱼子酱")
                || query.contains("蛋黄酱机") || query.contains("蛋黄酱")
                || query.contains("奶酪压制机") || query.contains("奶酪机") || query.contains("奶酪")
                || query.contains("织布机") || query.contains("布料机")
                || query.contains("产油机") || query.contains("榨油机") || query.contains("松露油")
                || query.contains("蜂房") || query.contains("蜂箱")
                || query.contains("熔炉") || query.contains("木炭窑") || query.contains("回收机")
                || query.contains("种子生产器") || query.contains("种子机")
                || query.contains("宝石复制机") || query.contains("水晶复制机")
                || query.contains("避雷针") || query.contains("鱼熏机") || query.contains("熏鱼")
                || query.contains("脱水机") || query.contains("风干机") || query.contains("果干") || query.contains("葡萄干")
                || query.contains("诱饵制造机") || query.contains("鱼饵制造机") || query.contains("定向鱼饵")
                || query.contains("蘑菇木桩") || query.contains("蘑菇木头") || query.contains("蘑菇桩")
                || query.contains("洒水器") || query.contains("洒水设备") || query.contains("灌溉设备")
                || query.contains("自动浇水") || query.contains("喷头")
                || query.contains("炸弹") || query.contains("楼梯") || query.contains("跳层") || query.contains("矿洞设备")
                || query.contains("箱子") || query.contains("储物箱") || query.contains("收纳箱")
                || query.contains("储物设备")
                || query.contains("标牌") || query.contains("木牌") || query.contains("石牌")
                || query.contains("稻草人") || query.contains("防乌鸦")
                || query.contains("重型熔炉") || query.contains("太阳能板") || query.contains("电池板")
                || query.contains("史莱姆孵化器") || query.contains("史莱姆蛋压制机")
                || query.contains("骨头磨坊") || query.contains("骨磨机")
                || query.contains("晶球破开器") || query.contains("晶球破碎机") || query.contains("自动开晶球")
                || query.contains("料斗") || query.contains("自动上料")
                || query.contains("农场电脑")
                || query.contains("肥料") || query.contains("生长激素") || query.contains("保湿土壤")
                || query.contains("保湿土") || query.contains("树肥")
                || query.contains("图腾") || query.contains("传送图腾") || query.contains("雨水图腾")
                || query.contains("宝藏图腾") || query.contains("怪物香水") || query.contains("仙尘")
                || query.contains("戒指") || query.contains("铱环") || query.contains("尤巴戒指")
                || query.contains("约巴戒指") || query.contains("光辉戒指") || query.contains("荆棘戒指")
                || query.contains("结婚戒指")
                || query.contains("钓具") || query.contains("鱼饵") || query.contains("魔法鱼饵")
                || query.contains("高级鱼饵") || query.contains("野性鱼饵") || query.contains("挑战鱼饵")
                || query.contains("浮标") || query.contains("旋式鱼饵") || query.contains("精装旋式鱼饵")
                || query.contains("寻宝器") || query.contains("宝藏猎人") || query.contains("倒刺钩")
                || query.contains("磁铁") || query.contains("蟹笼怎么做") || query.contains("蟹笼材料")
                || query.contains("蟹笼配方");
    }

    private boolean looksLikeCookingQuery(String query) {
        return query.contains("烹饪") || query.contains("料理") || query.contains("做饭")
                || query.contains("食谱") || query.contains("菜谱") || query.contains("菜")
                || query.contains("吃什么") || query.contains("食物")
                || query.contains("buff") || query.contains("增益")
                || query.contains("香辣鳗鱼") || query.contains("幸运午餐") || query.contains("海泡布丁")
                || query.contains("蟹黄糕") || query.contains("南瓜汤") || query.contains("魔法糖冰棍")
                || query.contains("三倍浓缩咖啡") || query.contains("咖啡") || query.contains("姜汁汽水")
                || (looksLikeCookingFoodName(query) && (query.contains("怎么做") || query.contains("材料")
                || query.contains("配方") || query.contains("效果")));
    }

    private boolean looksLikeCookingFoodName(String query) {
        return query.contains("蛋糕") || query.contains("汤") || query.contains("沙拉")
                || query.contains("披萨") || query.contains("寿司") || query.contains("生鱼片")
                || query.contains("煎蛋") || query.contains("蛋卷") || query.contains("早餐")
                || query.contains("面包") || query.contains("薄煎饼") || query.contains("薯饼")
                || query.contains("鱼肉卷") || query.contains("烤鱼") || query.contains("炸鱿鱼")
                || query.contains("蘑菇") || query.contains("火锅") || query.contains("山药")
                || query.contains("玉米饼") || query.contains("鲤鱼惊喜")
                || query.contains("派") || query.contains("曲奇") || query.contains("意大利面")
                || query.contains("鳗鱼") || query.contains("布丁") || query.contains("冰淇淋")
                || query.contains("千层酥") || query.contains("红之盛宴") || query.contains("秋日恩赐")
                || query.contains("超级大餐") || query.contains("蔓越莓酱") || query.contains("填料")
                || query.contains("蘸酱") || query.contains("炒菜") || query.contains("烤榛子")
                || query.contains("脆皮饼") || query.contains("糖果") || query.contains("烤面包")
                || query.contains("炖饭") || query.contains("烩饭") || query.contains("松糕")
                || query.contains("杂烩汤") || query.contains("浓汤") || query.contains("田螺")
                || query.contains("蜗牛") || query.contains("虾鸡尾酒") || query.contains("芒果糯米饭")
                || query.contains("芋泥") || query.contains("咖喱") || query.contains("墨汁意大利饺")
                || query.contains("意大利饺") || query.contains("苔藓汤");
    }

    private boolean isBroadCookingQuery(String query) {
        return query.contains("吃什么") || query.contains("推荐") || query.contains("哪些")
                || query.contains("有什么") || query.contains("列表") || query.contains("料理")
                || query.contains("烹饪") || query.contains("buff") || query.contains("增益");
    }

    private boolean looksLikeShopQuery(String query) {
        return query.contains("买") || query.contains("购买") || query.contains("哪里卖")
                || query.contains("在哪里买") || query.contains("哪里买")
                || query.contains("商店") || query.contains("商人") || query.contains("营业")
                || query.contains("开门") || query.contains("关门") || query.contains("几点")
                || query.contains("背包") || query.contains("货车") || query.contains("书商")
                || query.contains("兑换") || query.contains("换什么");
    }

    private boolean isMachineCraftingQuestion(String query, Optional<StardewData.Machine> machine) {
        return machine.isPresent()
                && (query.contains("材料") || query.contains("配方") || query.contains("加工") || query.contains("机器")
                || query.contains("设备") || query.contains("产出") || query.contains("值钱")
                || query.contains("收益") || query.contains("怎么做") || query.contains("怎么制作")
                || query.contains("能产") || query.contains("配方来源"));
    }

    private boolean shouldPreferResourceOverMachine(
            String query,
            StardewData.ResourceGuide resource,
            Optional<StardewData.Machine> machine
    ) {
        String normalizedQuery = StardewKnowledgeRepository.normalize(query);
        if (machine.isPresent() && containsNormalized(normalizedQuery, machine.get().getName())) {
            return false;
        }
        return resource != null && containsNormalized(normalizedQuery, resource.getName());
    }

    private boolean shouldPreferGuideOverCrop(String query, StardewData.GuideTopic guide) {
        if (guide == null || StringUtils.isBlank(guide.getCategory())) {
            return false;
        }
        if (!Set.of("progression", "mining", "collection", "crafting", "buildings").contains(guide.getCategory())) {
            return false;
        }
        return query.contains("怎么") || query.contains("解锁") || query.contains("修")
                || query.contains("准备") || query.contains("路线") || query.contains("攻略");
    }

    private boolean shouldPreferGuideOverBuilding(String query, StardewData.GuideTopic guide) {
        if (guide == null || !"animals".equals(guide.getCategory())) {
            return false;
        }
        return query.contains("怎么养") || query.contains("照顾") || query.contains("喂")
                || query.contains("喂食") || query.contains("心情") || query.contains("好感")
                || query.contains("动物产品") || query.contains("动物养殖");
    }

    private boolean shouldPreferGuideOverResource(String query, StardewData.GuideTopic guide) {
        if (guide == null || !"fruit_trees".equals(guide.getId())) {
            return false;
        }
        return (query.contains("果树") || query.contains("树苗") || query.contains("水果树"))
                && (query.contains("怎么种") || query.contains("温室") || query.contains("布局")
                || query.contains("间隔") || query.contains("3x3") || query.contains("3×3")
                || query.contains("几天成熟") || query.contains("成熟"));
    }

    private boolean shouldPreferGuideOverShop(String query, StardewData.GuideTopic guide) {
        if (guide == null || !"skill_books".equals(guide.getId())) {
            return false;
        }
        return query.contains("有什么用") || query.contains("效果") || query.contains("值得")
                || query.contains("推荐") || query.contains("优先") || query.contains("读了");
    }

    private boolean shouldPreferBookGuideOverShop(String query) {
        return looksLikeBookDetailQuery(query) && !containsAny(query, "在哪里买", "哪里买", "谁卖", "购买");
    }

    private boolean shouldPreferForgeGuide(String query, StardewData.GuideTopic guide) {
        return guide != null && "forge_enchanting".equals(guide.getId())
                && containsAny(query, "锻造", "附魔", "火山晶石", "银河之魂", "无限武器",
                "无限之刃", "无限之锤", "无限匕首", "戒指合成", "戒指组合", "forge", "enchant");
    }

    private boolean looksLikeSpecificSpecialCurrencyResourceQuery(String query) {
        if (!containsAny(query, "怎么获得", "获取", "哪里", "哪刷", "怎么刷", "在哪", "来源", "怎么拿",
                "怎么用", "有什么用", "换什么", "兑换", "花", "买什么", "优先")) {
            return false;
        }
        return containsAny(query,
                "齐钻", "齐先生宝石", "金核桃", "黄金核桃", "齐币", "赌场币", "星星币", "星之币",
                "奖券", "兑奖券", "三花蛋", "卡利科蛋", "火山晶石", "金色标签", "黄金标签",
                "Qi Gem", "Qi Gems", "Golden Walnut", "Golden Walnuts", "Qi Coin", "Qi Coins",
                "Star Token", "Star Tokens", "Prize Ticket", "Prize Tickets", "Calico Egg", "Calico Eggs",
                "Cinder Shard", "Cinder Shards", "Golden Tag", "Golden Tags");
    }

    private boolean looksLikeBookDetailQuery(String query) {
        return query.contains("有什么用") || query.contains("效果") || query.contains("值得")
                || query.contains("推荐") || query.contains("优先") || query.contains("读了")
                || query.contains("重复") || query.contains("再读") || query.contains("怎么获得")
                || query.contains("获取") || query.contains("来源") || query.contains("怎么拿")
                || query.contains("怎么解锁") || query.contains("解锁");
    }

    private boolean isBroadBookQuery(String query) {
        return query.contains("书籍有哪些") || query.contains("书有哪些") || query.contains("力量书有哪些")
                || query.contains("技能书有哪些") || query.contains("书籍推荐") || query.contains("技能书推荐")
                || query.contains("书商什么时候") || query.contains("书商在哪") || query.contains("书商卖什么");
    }

    private boolean shouldPreferGuideOverCooking(String query, StardewData.GuideTopic guide) {
        if (guide == null) {
            return false;
        }
        if ("skill_books".equals(guide.getId())) {
            return query.contains("书") || query.contains("书籍") || query.contains("书商")
                    || query.contains("酱料女皇食谱") || query.contains("Queen Of Sauce Cookbook");
        }
        if ("skill_food_buffs".equals(guide.getId())) {
            return query.contains("叠加") || query.contains("覆盖") || query.contains("规则")
                    || query.contains("机制") || query.contains("能一起") || query.contains("能同时")
                    || query.contains("互相覆盖");
        }
        return false;
    }

    private boolean looksLikeGuideQuery(String query) {
        return query.contains("升级") || query.contains("多少钱") || query.contains("金钱")
                || query.contains("条件") || query.contains("材料") || query.contains("建造")
                || query.contains("建筑") || query.contains("房屋") || query.contains("鸡舍")
                || query.contains("畜棚") || query.contains("鱼竿") || query.contains("作物")
                || query.contains("种什么") || query.contains("收益") || query.contains("技能")
                || query.contains("等级") || query.contains("快速") || query.contains("礼物")
                || query.contains("好感") || query.contains("生日") || query.contains("沙漠")
                || query.contains("温室") || query.contains("博物馆") || query.contains("鱼塘")
                || query.contains("骷髅洞穴") || query.contains("头骨洞穴") || query.contains("烹饪")
                || query.contains("料理") || query.contains("做饭") || query.contains("制作")
                || query.contains("合成") || query.contains("精通") || query.contains("书商")
                || query.contains("技能书") || query.contains("职业") || query.contains("洗点")
                || query.contains("buff") || query.contains("增益") || query.contains("锻造")
                || query.contains("附魔") || query.contains("银河之魂") || query.contains("无限武器")
                || query.contains("戒指合成") || query.contains("火山晶石");
    }

    private boolean containsAny(String query, String... values) {
        for (String value : values) {
            if (query.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBroadFishQuery(String query) {
        String normalized = StardewKnowledgeRepository.normalize(query);
        return "鱼".equals(normalized)
                || query.contains("什么鱼")
                || query.contains("哪些鱼")
                || query.contains("能钓")
                || query.contains("可钓")
                || query.contains("能抓")
                || query.contains("可抓")
                || query.contains("能捕")
                || query.contains("可捕")
                || query.contains("抓什么")
                || isBroadJellyQuery(query);
    }

    private boolean isBroadFishPondQuery(String query) {
        String normalized = StardewKnowledgeRepository.normalize(query);
        return "鱼塘".equals(normalized)
                || query.contains("鱼塘攻略")
                || query.contains("养什么")
                || query.contains("放什么")
                || query.contains("推荐")
                || query.contains("有哪些")
                || query.contains("列表")
                || query.contains("产物");
    }

    private boolean isBroadSpecialOrderQuery(String query) {
        String normalized = StardewKnowledgeRepository.normalize(query);
        return "特别订单".equals(normalized)
                || "特殊订单".equals(normalized)
                || query.contains("特别订单有哪些")
                || query.contains("特殊订单有哪些")
                || query.contains("订单板有哪些")
                || query.contains("任务板有哪些")
                || query.contains("特别订单列表")
                || query.contains("特殊订单列表")
                || query.contains("特别订单总览")
                || query.contains("齐先生任务有哪些")
                || query.contains("核桃房任务有哪些");
    }

    private boolean isBroadJellyQuery(String query) {
        return query.contains("果冻")
                && !query.contains("海果冻")
                && !query.contains("海水果冻")
                && !query.contains("河果冻")
                && !query.contains("淡水果冻")
                && !query.contains("洞穴果冻")
                && !query.contains("矿洞果冻");
    }

    private boolean isBroadCropQuery(String query) {
        return query.contains("作物") || query.contains("种什么") || query.contains("种植")
                || query.contains("农作物") || (query.contains("收益") && parseSeason(query) != null);
    }

    private boolean isBroadBuildingQuery(String query) {
        return query.contains("农场建筑") || query.contains("建筑有哪些") || query.contains("建筑列表")
                || query.contains("能建什么") || query.contains("可以建什么")
                || query.contains("哪些建筑") || query.contains("建筑推荐")
                || query.contains("方尖塔有哪些") || query.contains("方尖碑有哪些")
                || query.contains("魔法建筑有哪些") || query.contains("后期建筑有哪些")
                || query.contains("特殊建筑有哪些") || query.contains("社区升级有哪些");
    }

    private boolean isBroadMachineQuery(String query) {
        return query.contains("机器有哪些") || query.contains("机器列表")
                || query.contains("加工设备有哪些") || query.contains("工匠设备有哪些")
                || query.contains("制作设备有哪些") || query.contains("加工机器")
                || query.contains("赚钱机器推荐") || query.contains("储物设备")
                || query.contains("矿洞设备") || query.contains("洒水设备")
                || query.contains("灌溉设备") || query.contains("农场工具")
                || query.contains("肥料有哪些") || query.contains("肥料列表")
                || query.contains("保湿土壤有哪些") || query.contains("生长激素有哪些")
                || query.contains("图腾有哪些") || query.contains("传送图腾有哪些")
                || query.contains("戒指有哪些") || query.contains("戒指列表")
                || query.contains("消耗品有哪些")
                || query.contains("钓具有哪些") || query.contains("鱼饵有哪些")
                || query.contains("钓鱼装备有哪些") || query.contains("钓鱼制作有哪些");
    }

    private boolean isBroadCraftingQuery(String query) {
        return isBroadMachineQuery(query)
                || query.contains("制作配方有哪些")
                || query.contains("制作列表")
                || query.contains("配方列表")
                || query.contains("合成列表")
                || query.contains("能做什么")
                || query.contains("可以做什么")
                || query.contains("栅栏有哪些")
                || query.contains("地板有哪些")
                || query.contains("小径有哪些")
                || query.contains("照明有哪些")
                || query.contains("家具配方有哪些")
                || query.contains("种子配方有哪些")
                || query.contains("野生种子有哪些");
    }

    private String parseMachineCategory(String query) {
        if (query.contains("鱼饵") || query.contains("钓具") || query.contains("浮标")
                || query.contains("旋式") || query.contains("寻宝") || query.contains("宝藏猎人")
                || query.contains("倒刺钩") || query.contains("磁铁") || query.contains("蟹笼")) {
            return "fishing";
        }
        if (query.contains("栅栏") || query.contains("围栏") || query.contains("大门")) {
            return "fence";
        }
        if (query.contains("种子") || query.contains("草籽") || query.contains("茶苗")
                || query.contains("树种子")) {
            return "seed";
        }
        if (query.contains("地板") || query.contains("小径") || query.contains("路径")
                || query.contains("木径")) {
            return "decor";
        }
        if (query.contains("火把") || query.contains("营火") || query.contains("火盆")
                || query.contains("灯柱") || query.contains("南瓜灯") || query.contains("照明")) {
            return "lighting";
        }
        if (query.contains("家具") || query.contains("花桶") || query.contains("邪恶雕像")
                || query.contains("长笛块") || query.contains("鼓块")) {
            return "furniture";
        }
        if (query.contains("野外小食") || query.contains("虫肉牛排") || query.contains("生命药水")
                || query.contains("蒜油")) {
            return "edible";
        }
        if (query.contains("肥料") || query.contains("保湿土壤") || query.contains("保湿土")
                || query.contains("生长激素") || query.contains("树肥")) {
            return "fertilizer";
        }
        if (query.contains("图腾") || query.contains("传送")) {
            return "totem";
        }
        if (query.contains("戒指") || query.contains("铱环") || query.contains("尤巴")
                || query.contains("约巴") || query.contains("荆棘") || query.contains("光辉")) {
            return "ring";
        }
        if (query.contains("怪物香水") || query.contains("仙尘") || query.contains("消耗品")) {
            return "consumable";
        }
        if (query.contains("工匠") || query.contains("加工") || query.contains("赚钱")
                || query.contains("酒") || query.contains("果酱") || query.contains("奶酪")
                || query.contains("蛋黄酱") || query.contains("松露油")) {
            return "artisan";
        }
        if (query.contains("精炼") || query.contains("资源") || query.contains("熔炉")
                || query.contains("回收") || query.contains("避雷") || query.contains("复制")
                || query.contains("诱饵") || query.contains("蘑菇")) {
            return "refining";
        }
        if (query.contains("洒水") || query.contains("浇水") || query.contains("喷头")) {
            return "irrigation";
        }
        if (query.contains("炸弹") || query.contains("楼梯") || query.contains("矿洞")
                || query.contains("骷髅洞穴") || query.contains("跳层")) {
            return "mining";
        }
        if (query.contains("箱子") || query.contains("储物") || query.contains("收纳")
                || query.contains("标牌") || query.contains("牌子") || query.contains("告示牌")) {
            return "storage";
        }
        if (query.contains("稻草人") || query.contains("农场工具") || query.contains("农场电脑")
                || query.contains("防乌鸦")) {
            return "farm_utility";
        }
        if (query.contains("史莱姆")) {
            return "slime";
        }
        if (query.contains("自动化") || query.contains("料斗") || query.contains("自动上料")) {
            return "automation";
        }
        if (query.contains("野炊") || query.contains("帐篷") || query.contains("铁砧")
                || query.contains("锻造台") || query.contains("迷你锻造") || query.contains("转化")
                || query.contains("点唱机") || query.contains("方尖塔") || query.contains("雕像")
                || query.contains("爆炸弹药") || query.contains("花盆")) {
            return "misc";
        }
        return null;
    }

    private String formatMachineCategory(String category) {
        return switch (category) {
            case "artisan" -> "工匠/加工";
            case "refining" -> "精炼/资源";
            case "irrigation" -> "洒水/灌溉";
            case "mining" -> "矿洞/炸弹";
            case "storage" -> "储物/标记";
            case "farm_utility" -> "农场工具";
            case "slime" -> "史莱姆";
            case "automation" -> "自动化";
            case "fertilizer" -> "肥料/土壤";
            case "totem" -> "图腾/传送";
            case "ring" -> "戒指";
            case "consumable" -> "一次性消耗品";
            case "fishing" -> "钓鱼装备";
            case "fence" -> "栅栏/大门";
            case "seed" -> "种子/茶苗";
            case "decor" -> "地板/小径";
            case "lighting" -> "照明";
            case "furniture" -> "家具";
            case "edible" -> "可食用制作物";
            case "misc" -> "杂项";
            default -> category;
        };
    }

    private boolean isCrabPotCatchQuery(String query) {
        return query.contains("蟹笼")
                && !query.contains("怎么做")
                && !query.contains("怎么制作")
                && !query.contains("材料")
                && !query.contains("配方")
                && (query.contains("能抓") || query.contains("可抓") || query.contains("抓什么")
                || query.contains("能捕") || query.contains("可捕") || query.contains("产物")
                || query.contains("淡水") || query.contains("海水"));
    }

    private String parseBuildingCategory(String query) {
        if (query.contains("动物") || query.contains("鸡舍") || query.contains("畜棚") || query.contains("养")) {
            return "animals";
        }
        if (query.contains("社区升级") || query.contains("潘姆房") || query.contains("潘姆房子")
                || query.contains("城镇捷径") || query.contains("小镇捷径") || query.contains("社区捷径")) {
            return "community";
        }
        if (query.contains("房屋") || query.contains("房子") || query.contains("地窖")) {
            return "housing";
        }
        if (query.contains("加工") || query.contains("磨坊")) {
            return "processing";
        }
        if (query.contains("储存") || query.contains("筒仓") || query.contains("出货")) {
            return "storage";
        }
        if (query.contains("方尖塔") || query.contains("方尖碑") || query.contains("黄金钟")
                || query.contains("黄金时钟") || query.contains("金钟") || query.contains("祝尼魔")
                || query.contains("法师塔") || query.contains("魔法建筑") || query.contains("后期建筑")) {
            return "magic";
        }
        if (query.contains("马厩") || query.contains("传送") || query.contains("交通")) {
            return "transport";
        }
        return null;
    }

    private String formatBuildingCategory(String category) {
        return switch (category) {
            case "animals" -> "动物设施";
            case "housing" -> "房屋升级";
            case "processing" -> "加工设施";
            case "storage" -> "储存/出货";
            case "transport" -> "交通";
            case "magic" -> "魔法/后期";
            case "community" -> "社区升级";
            default -> category;
        };
    }

    private String formatGold(Integer gold) {
        if (gold == null || gold == 0) {
            return "无固定金币花费";
        }
        return String.format(Locale.ROOT, "%,dg", gold);
    }

    private String formatMaterials(List<StardewData.MaterialCost> materials) {
        if (materials == null || materials.isEmpty()) {
            return "无额外材料";
        }
        List<String> parts = new ArrayList<>();
        for (StardewData.MaterialCost material : materials) {
            if (material.getQuantity() == null) {
                parts.add(material.getName());
            } else {
                parts.add(material.getName() + " x" + material.getQuantity());
            }
        }
        return String.join("、", parts);
    }

    private String formatFoodBuffs(List<StardewData.FoodBuff> buffs) {
        if (buffs == null || buffs.isEmpty()) {
            return "无特殊技能增益";
        }
        List<String> parts = new ArrayList<>();
        for (StardewData.FoodBuff buff : buffs) {
            StringBuilder part = new StringBuilder(buff.getName());
            if (buff.getValue() != null) {
                part.append(buff.getValue() > 0 ? " +" : " ").append(buff.getValue());
            }
            if (StringUtils.isNotBlank(buff.getDuration())) {
                part.append("（").append(buff.getDuration()).append("）");
            }
            parts.add(part.toString());
        }
        return String.join("、", parts);
    }

    private boolean containsNormalized(String query, String value) {
        String normalized = StardewKnowledgeRepository.normalize(value);
        return StringUtils.isNotBlank(normalized) && query.contains(normalized);
    }

    private boolean containsAnyNormalized(String query, List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            String normalized = StardewKnowledgeRepository.normalize(value);
            if (StringUtils.isNotBlank(normalized) && (query.contains(normalized)
                    || (query.contains("新手") && normalized.contains("新手"))
                    || (query.contains("动物") && normalized.contains("动物"))
                    || (query.contains("钓鱼") && normalized.contains("钓鱼"))
                    || (query.contains("采矿") && normalized.contains("采矿"))
                    || (query.contains("挖矿") && normalized.contains("采矿"))
                    || (query.contains("硬木") && normalized.contains("硬木"))
                    || (query.contains("多人") && normalized.contains("多人"))
                    || (query.contains("战斗") && normalized.contains("战斗"))
                    || (query.contains("洒水器") && normalized.contains("洒水器"))
                    || (query.contains("赚钱") && normalized.contains("赚钱"))
                    || (query.contains("前期") && normalized.contains("前期"))
                    || (query.contains("后期") && normalized.contains("后期"))
                    || (query.contains("收集包") && normalized.contains("收集包")))) {
                return true;
            }
        }
        return false;
    }

    private String formatTimeWindows(List<StardewData.TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return "任意时间";
        }
        List<String> parts = new ArrayList<>();
        for (StardewData.TimeWindow w : windows) {
            parts.add(w.getStart() + "-" + w.getEnd());
        }
        return String.join("/", parts);
    }

    private String formatList(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : String.join("、", values);
    }

    private String formatFestivalDate(StardewData.FestivalEvent event) {
        if (event.getStartDay() == null) {
            return StringUtils.defaultIfBlank(event.getSeason(), "日期未记录");
        }
        StringBuilder date = new StringBuilder(StringUtils.defaultIfBlank(event.getSeason(), ""));
        date.append(event.getStartDay()).append("日");
        if (event.getEndDay() != null && !event.getEndDay().equals(event.getStartDay())) {
            date.append("-").append(event.getEndDay()).append("日");
        }
        return date.toString();
    }

    private int seasonOrder(String season) {
        if ("春季".equals(season)) return 1;
        if ("夏季".equals(season)) return 2;
        if ("秋季".equals(season)) return 3;
        if ("冬季".equals(season)) return 4;
        return 9;
    }

    private String formatMinutes(int minutes) {
        int hour = minutes / 60;
        int minute = minutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private List<String> collectFishSources(List<StardewData.Fish> fish) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Fish f : fish) {
            if (f.getSourceUrls() != null) {
                urls.addAll(f.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectCropSources(List<StardewData.Crop> crops) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Crop c : crops) {
            if (c.getSourceUrls() != null) {
                urls.addAll(c.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectBuildingSources(List<StardewData.Building> buildings) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Building b : buildings) {
            if (b.getSourceUrls() != null) {
                urls.addAll(b.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectToolSources(List<StardewData.Tool> tools) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Tool tool : tools) {
            if (tool.getSourceUrls() != null) {
                urls.addAll(tool.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectMachineSources(List<? extends StardewData.Machine> machines) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Machine machine : machines) {
            if (machine.getSourceUrls() != null) {
                urls.addAll(machine.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectCookingSources(List<StardewData.CookingRecipe> recipes) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.CookingRecipe recipe : recipes) {
            if (recipe.getSourceUrls() != null) {
                urls.addAll(recipe.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectFishPondSources(List<StardewData.FishPondGuide> fishPonds) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.FishPondGuide fishPond : fishPonds) {
            if (fishPond.getSourceUrls() != null) {
                urls.addAll(fishPond.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectBookSources(List<StardewData.BookGuide> books) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.BookGuide book : books) {
            if (book.getSourceUrls() != null) {
                urls.addAll(book.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectFestivalSources(List<StardewData.FestivalEvent> events) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.FestivalEvent event : events) {
            if (event.getSourceUrls() != null) {
                urls.addAll(event.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectFarmMapSources(List<StardewData.FarmMapGuide> maps) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.FarmMapGuide map : maps) {
            if (map.getSourceUrls() != null) {
                urls.addAll(map.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectFarmAnimalSources(List<StardewData.FarmAnimalGuide> animals) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.FarmAnimalGuide animal : animals) {
            if (animal.getSourceUrls() != null) {
                urls.addAll(animal.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectDungeonSources(List<StardewData.DungeonGuide> dungeons) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.DungeonGuide dungeon : dungeons) {
            if (dungeon.getSourceUrls() != null) {
                urls.addAll(dungeon.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectIslandSources(List<StardewData.IslandGuide> islands) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.IslandGuide island : islands) {
            if (island.getSourceUrls() != null) {
                urls.addAll(island.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private List<String> collectStoryQuestSources(List<StardewData.StoryQuestGuide> quests) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.StoryQuestGuide quest : quests) {
            if (quest.getSourceUrls() != null) {
                urls.addAll(quest.getSourceUrls());
            }
        }
        return new ArrayList<>(urls);
    }

    private StardewGuideResult result(String intent, String answer, List<String> urls) {
        return StardewGuideResult.builder()
                .intent(intent)
                .answer(answer)
                .sourceUrls(urls == null ? List.of() : urls)
                .gameVersion(repository.gameVersion())
                .lastCheckedAt(repository.lastCheckedAt())
                .build();
    }

    private String help() {
        return "可以这样问：\n"
                + "- 星露谷 夏天能钓什么鱼\n"
                + "- 星露谷 蟹笼能抓什么\n"
                + "- 星露谷 传说鱼有哪些\n"
                + "- 星露谷 果冻怎么钓\n"
                + "- 星露谷 海鱼收集包需要什么\n"
                + "- 星露谷 阿比盖尔 夏季 12日 15:00 晴天在哪\n"
                + "- 星露谷 阿比盖尔喜欢什么礼物\n"
                + "- 星露谷 背包升级多少钱\n"
                + "- 星露谷 铱制洒水器在哪里买\n"
                + "- 星露谷 硬木怎么获取\n"
                + "- 星露谷 斧头升级需要什么\n"
                + "- 星露谷 钢斧需要什么材料\n"
                + "- 星露谷 鸡舍升级材料多少钱\n"
                + "- 星露谷 解锁猪需要什么\n"
                + "- 星露谷 鱼熏机需要什么\n"
                + "- 星露谷 小桶和罐头瓶怎么做\n"
                + "- 星露谷 幸运午餐怎么做\n"
                + "- 星露谷 骷髅洞穴吃什么料理 buff 好\n"
                + "- 星露谷 夏季种什么收益好\n"
                + "- 星露谷 蓝莓几天成熟";
    }

    private static class QueryContext {
        String season;
        Integer day;
        String weekday;
        String weather;
        String location;
        Integer timeMinutes;
        String timeText;
    }

    private record ShopStockMatch(StardewData.Shop shop, StardewData.ShopItem item, int score) {
    }
}
