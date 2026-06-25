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

    public StardewGuideResult answer(String rawQuery) {
        String query = cleanQuery(rawQuery);
        if (StringUtils.isBlank(query)) {
            return result("help", help(), List.of());
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
        Optional<StardewData.GuideTopic> guide = repository.findGuide(query);

        boolean bundleIntent = query.contains("收集包") || query.contains("献祭")
                || (bundle.isPresent() && (query.contains("要") || query.contains("需要") || query.contains("交") || query.contains("包")));
        if (bundleIntent && (bundle.isPresent() || (crop.isEmpty() && building.isEmpty()))) {
            return bundle.map(b -> bundleAnswer(query, b))
                    .orElseGet(() -> wikiFallbackAnswer(query, "没找到对应收集包。可以试试：海鱼收集包、湖鱼收集包、工匠收集包、锅炉房。"));
        }
        if (villager.isPresent() && looksLikeScheduleQuery(query)) {
            return villagerAnswer(query, villager.get());
        }
        if (villager.isPresent() && looksLikeVillagerProfileQuery(query)) {
            return villagerProfileAnswer(villager.get());
        }
        if (tool.isPresent() || looksLikeToolQuery(query)) {
            if (tool.isPresent()) {
                return toolUpgradeAnswer(query, tool.get());
            }
            return toolUpgradeListAnswer();
        }
        if (looksLikeShopQuery(query) && shopItem.isPresent()) {
            return shopItemAnswer(query, shopItem.get());
        }
        if (looksLikeShopQuery(query) && shop.isPresent()) {
            return shopAnswer(shop.get());
        }
        if (building.isPresent() || looksLikeBuildingQuery(query)) {
            boolean broadBuildingQuery = isBroadBuildingQuery(query);
            if (building.isPresent() && !broadBuildingQuery) {
                return buildingDetailAnswer(building.get());
            }
            return buildingListAnswer(query);
        }
        if (resource.isPresent() && looksLikeResourceQuery(query)
                && !(fish.isPresent() && query.contains("果冻"))
                && !isFishingQuestion(query) && !isMachineCraftingQuestion(query, machine)) {
            return resourceAnswer(query, resource.get());
        }
        if (machine.isPresent() || looksLikeMachineQuery(query)) {
            boolean broadMachineQuery = isBroadMachineQuery(query);
            if (machine.isPresent() && !broadMachineQuery) {
                return machineDetailAnswer(machine.get());
            }
            return machineListAnswer(query);
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

    private StardewGuideResult toolUpgradeListAnswer() {
        StringBuilder sb = new StringBuilder();
        sb.append("工具升级总览：\n");
        for (StardewData.Tool tool : repository.tools()) {
            sb.append("- ").append(tool.getName()).append("：");
            if ("purchase".equals(tool.getCategory())) {
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
        sb.append("建议：斧头优先钢斧进秘密森林；喷壶看天气预报升级；镐子服务下矿效率；垃圾桶最后考虑。");
        return result("tool_upgrade_list", sb.toString(), collectToolSources(repository.tools()));
    }

    private StardewGuideResult toolUpgradeAnswer(String query, StardewData.Tool tool) {
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
        return result("tool_upgrade_detail", sb.toString().trim(), tool.getSourceUrls());
    }

    private Optional<StardewData.ToolUpgrade> selectToolUpgrade(String query, StardewData.Tool tool) {
        String q = StardewKnowledgeRepository.normalize(query);
        return tool.getUpgrades().stream()
                .filter(upgrade -> containsNormalized(q, upgrade.getName())
                        || containsNormalized(q, upgrade.getLevel() + "级"))
                .findFirst();
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
            return result("wiki_fallback_empty", fallbackText + "\n官方 Wiki 兜底检索也没有找到可用结果。", List.of());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本地结构化库暂无直接条目，已从官方 Wiki 检索：\n");
        for (int i = 0; i < pages.size(); i++) {
            StardewWikiPage page = pages.get(i);
            sb.append(i + 1).append(". ").append(page.getTitle()).append("\n");
            sb.append(trimExcerpt(page.getExcerpt())).append("\n");
        }
        sb.append("提示：这是 Wiki 兜底摘要；复杂机制建议点来源继续看原页。");
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
                || query.contains("铜斧") || query.contains("钢斧") || query.contains("金斧") || query.contains("铱斧")
                || query.contains("铜镐") || query.contains("钢镐") || query.contains("金镐") || query.contains("铱镐");
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
                || query.contains("蘑菇木桩") || query.contains("蘑菇木头") || query.contains("蘑菇桩");
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
                && (query.contains("怎么做") || query.contains("制作") || query.contains("材料")
                || query.contains("配方") || query.contains("加工") || query.contains("机器")
                || query.contains("设备") || query.contains("产出") || query.contains("值钱")
                || query.contains("收益"));
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
                || query.contains("buff") || query.contains("增益");
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
                || query.contains("哪些建筑") || query.contains("建筑推荐");
    }

    private boolean isBroadMachineQuery(String query) {
        return query.contains("机器有哪些") || query.contains("机器列表")
                || query.contains("加工设备有哪些") || query.contains("工匠设备有哪些")
                || query.contains("加工机器") || query.contains("赚钱机器推荐");
    }

    private String parseMachineCategory(String query) {
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
        return null;
    }

    private String formatMachineCategory(String category) {
        return switch (category) {
            case "artisan" -> "工匠/加工";
            case "refining" -> "精炼/资源";
            default -> category;
        };
    }

    private String parseBuildingCategory(String query) {
        if (query.contains("动物") || query.contains("鸡舍") || query.contains("畜棚") || query.contains("养")) {
            return "animals";
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
            default -> category;
        };
    }

    private String formatGold(Integer gold) {
        if (gold == null) {
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

    private boolean containsNormalized(String query, String value) {
        String normalized = StardewKnowledgeRepository.normalize(value);
        return StringUtils.isNotBlank(normalized) && query.contains(normalized);
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

    private List<String> collectMachineSources(List<StardewData.Machine> machines) {
        Set<String> urls = new LinkedHashSet<>();
        for (StardewData.Machine machine : machines) {
            if (machine.getSourceUrls() != null) {
                urls.addAll(machine.getSourceUrls());
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
