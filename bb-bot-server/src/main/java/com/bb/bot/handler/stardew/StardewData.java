package com.bb.bot.handler.stardew;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
public class StardewData {

    private String gameVersion;
    private String lastCheckedAt;
    private List<Fish> fish = new ArrayList<>();
    private List<Bundle> bundles = new ArrayList<>();
    private List<Crop> crops = new ArrayList<>();
    private List<Building> buildings = new ArrayList<>();
    private List<Tool> tools = new ArrayList<>();
    private List<CraftingRecipe> craftingRecipes = new ArrayList<>();
    private List<Machine> machines = new ArrayList<>();
    private List<Shop> shops = new ArrayList<>();
    private List<Villager> villagers = new ArrayList<>();
    private List<ResourceGuide> resources = new ArrayList<>();
    private List<MonsterDropGuide> monsterDrops = new ArrayList<>();
    private List<FishPondGuide> fishPonds = new ArrayList<>();
    private List<CookingRecipe> cookingRecipes = new ArrayList<>();
    private List<BookGuide> books = new ArrayList<>();
    private List<SpecialOrderGuide> specialOrders = new ArrayList<>();
    private List<SkillGuide> skillGuides = new ArrayList<>();
    private List<FestivalEvent> festivalEvents = new ArrayList<>();
    private List<GuideTopic> guides = new ArrayList<>();

    @Data
    public static class Fish {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<String> locations = new ArrayList<>();
        private List<String> seasons = new ArrayList<>();
        private List<TimeWindow> timeWindows = new ArrayList<>();
        private List<String> weather = new ArrayList<>();
        private List<String> usedIn = new ArrayList<>();
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class Bundle {
        private String id;
        private String name;
        private String room;
        private List<String> aliases = new ArrayList<>();
        private String reward;
        private List<BundleItem> items = new ArrayList<>();
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class BundleItem {
        private String name;
        private Integer quantity = 1;
        private String quality;
        private String hint;
    }

    @Data
    public static class Crop {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<String> seasons = new ArrayList<>();
        private String seedName;
        private Integer seedPrice;
        private List<String> seedSources = new ArrayList<>();
        private Integer growDays;
        private Integer regrowDays;
        private Integer sellPrice;
        private Double goldPerDay;
        private List<String> usedIn = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class Building {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String category;
        private Integer cost;
        private List<MaterialCost> materials = new ArrayList<>();
        private String size;
        private String buildTime;
        private String prerequisite;
        private List<String> unlocks = new ArrayList<>();
        private List<String> houses = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private String recommendation;
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class MaterialCost {
        private String name;
        private Integer quantity;
    }

    @Data
    public static class Tool {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String category;
        private String upgradeLocation;
        private String upgradeTime;
        private String recommendation;
        private String note;
        private List<ToolUpgrade> upgrades = new ArrayList<>();
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class ToolUpgrade {
        private String level;
        private String name;
        private Integer cost;
        private List<MaterialCost> materials = new ArrayList<>();
        private String effect;
        private String prerequisite;
    }

    @Data
    public static class Machine {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String category;
        private String recipeSource;
        private List<MaterialCost> materials = new ArrayList<>();
        private List<String> inputs = new ArrayList<>();
        private List<String> outputs = new ArrayList<>();
        private String processingTime;
        private String formula;
        private String recommendation;
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CraftingRecipe extends Machine {
    }

    @Data
    public static class Shop {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String location;
        private String openHours;
        private String closed;
        private String unlockCondition;
        private String currency;
        private List<ShopItem> stock = new ArrayList<>();
        private String recommendation;
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class ShopItem {
        private String name;
        private List<String> aliases = new ArrayList<>();
        private Integer price;
        private String currency;
        private String availability;
        private String note;
    }

    @Data
    public static class Villager {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String livesAt;
        private String birthday;
        private List<String> lovedGifts = new ArrayList<>();
        private List<String> likedGifts = new ArrayList<>();
        private String giftNote;
        private List<ScheduleRule> schedules = new ArrayList<>();
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class ScheduleRule {
        private String label;
        private Integer priority = 0;
        private Condition condition = new Condition();
        private List<ScheduleEvent> events = new ArrayList<>();
        private String note;
    }

    @Data
    public static class ScheduleEvent {
        private String time;
        private String location;
    }

    @Data
    public static class Condition {
        private List<String> seasons = new ArrayList<>();
        private List<Integer> days = new ArrayList<>();
        private List<String> weekdays = new ArrayList<>();
        private List<String> weather = new ArrayList<>();
        private String note;
    }

    @Data
    public static class ResourceGuide {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<Acquisition> acquisitions = new ArrayList<>();
        private String recommendation;
        private List<String> usedIn = new ArrayList<>();
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class Acquisition {
        private String type;
        private String detail;
    }

    @Data
    public static class MonsterDropGuide {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<String> locations = new ArrayList<>();
        private String floors;
        private String xp;
        private List<String> drops = new ArrayList<>();
        private String recommendation;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class FishPondGuide {
        private String id;
        private String fishName;
        private String fishNameEn;
        private List<String> aliases = new ArrayList<>();
        private Integer initialCapacity;
        private Integer maxCapacity;
        private String spawnFrequency;
        private List<FishPondQuest> quests = new ArrayList<>();
        private List<FishPondProduct> products = new ArrayList<>();
        private String recommendation;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class FishPondQuest {
        private Integer population;
        private String itemsRequired;
    }

    @Data
    public static class FishPondProduct {
        private String item;
        private Integer requiredPopulation;
        private String itemChance;
        private String dailyChance;
    }

    @Data
    public static class CookingRecipe {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<MaterialCost> ingredients = new ArrayList<>();
        private String recipeSource;
        private String effect;
        private List<FoodBuff> buffs = new ArrayList<>();
        private List<String> tags = new ArrayList<>();
        private String recommendation;
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class FoodBuff {
        private String name;
        private Integer value;
        private String duration;
    }

    @Data
    public static class BookGuide {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String type;
        private String effect;
        private String repeatReading;
        private List<Acquisition> acquisitions = new ArrayList<>();
        private String recommendation;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class SpecialOrderGuide {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String board;
        private String requester;
        private String prerequisite;
        private String timeframe;
        private List<String> requirements = new ArrayList<>();
        private List<String> rewards = new ArrayList<>();
        private String repeatable;
        private String recommendation;
        private List<String> tips = new ArrayList<>();
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class SkillGuide {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();
        private List<GuideSection> sections = new ArrayList<>();
        private String recommendation;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class FestivalEvent {
        private String id;
        private String name;
        private String nameEn;
        private List<String> aliases = new ArrayList<>();
        private String season;
        private Integer startDay;
        private Integer endDay;
        private String location;
        private String entryTime;
        private String endTime;
        private String returnTime;
        private Boolean timePasses;
        private Boolean shopsClosed;
        private Boolean animalsNeedFeeding;
        private List<String> activities = new ArrayList<>();
        private List<String> rewards = new ArrayList<>();
        private List<String> shopHighlights = new ArrayList<>();
        private String recommendation;
        private String note;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class GuideTopic {
        private String id;
        private String name;
        private String category;
        private List<String> aliases = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();
        private List<GuideSection> sections = new ArrayList<>();
        private String recommendation;
        private List<String> sourceUrls = new ArrayList<>();
    }

    @Data
    public static class GuideSection {
        private String title;
        private List<String> lines = new ArrayList<>();
    }

    @Data
    public static class TimeWindow {
        private String start;
        private String end;
    }
}
