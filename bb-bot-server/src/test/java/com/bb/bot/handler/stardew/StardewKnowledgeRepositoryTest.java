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
        assertThat(repository.buildings()).hasSizeGreaterThanOrEqualTo(17);
        assertThat(repository.tools()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(repository.machines()).hasSizeGreaterThanOrEqualTo(16);
        assertThat(repository.shops()).hasSizeGreaterThanOrEqualTo(9);
        assertThat(repository.villagers()).hasSizeGreaterThanOrEqualTo(34);
        assertThat(repository.resources()).hasSizeGreaterThanOrEqualTo(19);
        assertThat(repository.cookingRecipes()).hasSizeGreaterThanOrEqualTo(19);
        assertThat(repository.guides()).hasSizeGreaterThanOrEqualTo(33);
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
                "crafting"
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
}
