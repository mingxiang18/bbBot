package com.bb.bot.handler.stardew;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertThat(repository.bundles()).hasSizeGreaterThanOrEqualTo(37);
        assertThat(repository.crops()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(repository.buildings()).hasSizeGreaterThanOrEqualTo(27);
        assertThat(repository.tools()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(repository.machines()).hasSizeGreaterThanOrEqualTo(80);
        assertThat(repository.shops()).hasSizeGreaterThanOrEqualTo(9);
        assertThat(repository.villagers()).hasSizeGreaterThanOrEqualTo(34);
        assertThat(repository.resources()).hasSizeGreaterThanOrEqualTo(91);
        assertThat(repository.cookingRecipes()).hasSizeGreaterThanOrEqualTo(70);
        assertThat(repository.guides()).hasSizeGreaterThanOrEqualTo(35);
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
                "animals"
        );
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
                "cranberry_candy"
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
