package com.bb.bot.handler.stardew;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class StardewKnowledgeRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StardewData data = new StardewData();

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource("stardew/guide-data.json").getInputStream()) {
            data = objectMapper.readValue(in, StardewData.class);
            log.info("Stardew knowledge loaded: fish={}, bundles={}, crops={}, buildings={}, tools={}, craftingRecipes={}, machines={}, shops={}, villagers={}, resources={}, monsterDrops={}, fishPonds={}, cookingRecipes={}, books={}, specialOrders={}, skillGuides={}, festivalEvents={}, farmMaps={}, farmAnimals={}, guides={}",
                    data.getFish().size(), data.getBundles().size(), data.getCrops().size(), data.getBuildings().size(),
                    data.getTools().size(), data.getCraftingRecipes().size(), data.getMachines().size(), data.getShops().size(), data.getVillagers().size(),
                    data.getResources().size(), data.getMonsterDrops().size(), data.getFishPonds().size(), data.getCookingRecipes().size(),
                    data.getBooks().size(), data.getSpecialOrders().size(), data.getSkillGuides().size(), data.getFestivalEvents().size(), data.getFarmMaps().size(), data.getFarmAnimals().size(), data.getGuides().size());
        } catch (Exception e) {
            log.error("Failed to load Stardew knowledge data", e);
            data = new StardewData();
        }
    }

    public String gameVersion() {
        return data.getGameVersion();
    }

    public String lastCheckedAt() {
        return data.getLastCheckedAt();
    }

    public List<StardewData.Fish> fish() {
        return safe(data.getFish());
    }

    public List<StardewData.Bundle> bundles() {
        return safe(data.getBundles());
    }

    public List<StardewData.Crop> crops() {
        return safe(data.getCrops());
    }

    public List<StardewData.Building> buildings() {
        return safe(data.getBuildings());
    }

    public List<StardewData.Tool> tools() {
        return safe(data.getTools());
    }

    public List<StardewData.CraftingRecipe> craftingRecipes() {
        if (data.getCraftingRecipes() == null || data.getCraftingRecipes().isEmpty()) {
            return machines().stream()
                    .map(this::asCraftingRecipe)
                    .toList();
        }
        return safe(data.getCraftingRecipes());
    }

    public List<StardewData.Machine> machines() {
        return safe(data.getMachines());
    }

    public List<StardewData.Shop> shops() {
        return safe(data.getShops());
    }

    public List<StardewData.Villager> villagers() {
        return safe(data.getVillagers());
    }

    public List<StardewData.ResourceGuide> resources() {
        return safe(data.getResources());
    }

    public List<StardewData.MonsterDropGuide> monsterDrops() {
        return safe(data.getMonsterDrops());
    }

    public List<StardewData.FishPondGuide> fishPonds() {
        return safe(data.getFishPonds());
    }

    public List<StardewData.CookingRecipe> cookingRecipes() {
        return safe(data.getCookingRecipes());
    }

    public List<StardewData.BookGuide> books() {
        return safe(data.getBooks());
    }

    public List<StardewData.SpecialOrderGuide> specialOrders() {
        return safe(data.getSpecialOrders());
    }

    public List<StardewData.SkillGuide> skillGuides() {
        return safe(data.getSkillGuides());
    }

    public List<StardewData.FestivalEvent> festivalEvents() {
        return safe(data.getFestivalEvents());
    }

    public List<StardewData.FarmMapGuide> farmMaps() {
        return safe(data.getFarmMaps());
    }

    public List<StardewData.FarmAnimalGuide> farmAnimals() {
        return safe(data.getFarmAnimals());
    }

    public List<StardewData.GuideTopic> guides() {
        return safe(data.getGuides());
    }

    public Optional<StardewData.Bundle> findBundle(String query) {
        String q = normalize(query);
        return bundles().stream()
                .map(b -> new BundleMatch(b, scoreBundle(q, b)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(BundleMatch::bundle)
                .findFirst();
    }

    public Optional<StardewData.Villager> findVillager(String query) {
        String q = normalize(query);
        return villagers().stream()
                .filter(v -> matches(q, v.getName(), v.getNameEn(), v.getAliases()))
                .findFirst();
    }

    public Optional<StardewData.ResourceGuide> findResource(String query) {
        String q = normalize(query);
        return resources().stream()
                .map(r -> new ResourceMatch(r,
                        scoreSearchable(q, r.getName(), r.getNameEn(), r.getAliases()),
                        firstSearchableIndex(q, r.getName(), r.getNameEn(), r.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> {
                    int score = Integer.compare(b.score(), a.score());
                    if (score != 0) {
                        return score;
                    }
                    return Integer.compare(a.firstIndex(), b.firstIndex());
                })
                .map(ResourceMatch::resource)
                .findFirst();
    }

    public Optional<StardewData.MonsterDropGuide> findMonsterDrop(String query) {
        String q = normalize(query);
        return monsterDrops().stream()
                .map(m -> new MonsterDropMatch(m, scoreSearchable(q, m.getName(), m.getNameEn(), m.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(MonsterDropMatch::monster)
                .findFirst();
    }

    public Optional<StardewData.FishPondGuide> findFishPond(String query) {
        String q = normalize(query);
        return fishPonds().stream()
                .map(f -> new FishPondMatch(f, scoreSearchable(q, f.getFishName(), f.getFishNameEn(), f.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(FishPondMatch::fishPond)
                .findFirst();
    }

    public Optional<StardewData.CookingRecipe> findCookingRecipe(String query) {
        String q = normalize(query);
        return cookingRecipes().stream()
                .map(r -> new CookingRecipeMatch(r, scoreSearchable(q, r.getName(), r.getNameEn(), r.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(CookingRecipeMatch::recipe)
                .findFirst();
    }

    public Optional<StardewData.Crop> findCrop(String query) {
        String q = normalize(query);
        return crops().stream()
                .filter(c -> matches(q, c.getName(), c.getNameEn(), c.getAliases()))
                .findFirst();
    }

    public Optional<StardewData.Building> findBuilding(String query) {
        String q = normalize(query);
        return buildings().stream()
                .map(b -> new BuildingMatch(b, scoreSearchable(q, b.getName(), b.getNameEn(), b.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(BuildingMatch::building)
                .findFirst();
    }

    public Optional<StardewData.Tool> findTool(String query) {
        String q = normalize(query);
        return tools().stream()
                .map(t -> new ToolMatch(t, scoreSearchable(q, t.getName(), t.getNameEn(), t.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(ToolMatch::tool)
                .findFirst();
    }

    public Optional<StardewData.Machine> findMachine(String query) {
        String q = normalize(query);
        return machines().stream()
                .map(m -> new MachineMatch(m, scoreSearchable(q, m.getName(), m.getNameEn(), m.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(MachineMatch::machine)
                .findFirst();
    }

    public Optional<StardewData.CraftingRecipe> findCraftingRecipe(String query) {
        String q = normalize(query);
        return craftingRecipes().stream()
                .map(recipe -> new CraftingRecipeMatch(recipe, scoreSearchable(q, recipe.getName(), recipe.getNameEn(), recipe.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(CraftingRecipeMatch::recipe)
                .findFirst();
    }

    private StardewData.CraftingRecipe asCraftingRecipe(StardewData.Machine machine) {
        StardewData.CraftingRecipe recipe = new StardewData.CraftingRecipe();
        recipe.setId(machine.getId());
        recipe.setName(machine.getName());
        recipe.setNameEn(machine.getNameEn());
        recipe.setAliases(machine.getAliases());
        recipe.setCategory(machine.getCategory());
        recipe.setRecipeSource(machine.getRecipeSource());
        recipe.setMaterials(machine.getMaterials());
        recipe.setInputs(machine.getInputs());
        recipe.setOutputs(machine.getOutputs());
        recipe.setProcessingTime(machine.getProcessingTime());
        recipe.setFormula(machine.getFormula());
        recipe.setRecommendation(machine.getRecommendation());
        recipe.setNote(machine.getNote());
        recipe.setSourceUrls(machine.getSourceUrls());
        return recipe;
    }

    public Optional<StardewData.Shop> findShop(String query) {
        String q = normalize(query);
        return shops().stream()
                .map(s -> new ShopMatch(s, scoreSearchable(q, s.getName(), s.getNameEn(), s.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(ShopMatch::shop)
                .findFirst();
    }

    public Optional<StardewData.GuideTopic> findGuide(String query) {
        String q = normalize(query);
        return guides().stream()
                .map(g -> new GuideMatch(g, scoreGuide(q, g)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(GuideMatch::guide)
                .findFirst();
    }

    public Optional<StardewData.SkillGuide> findSkillGuide(String query) {
        String q = normalize(query);
        return skillGuides().stream()
                .map(g -> new SkillGuideMatch(g, scoreSkillGuide(q, g)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(SkillGuideMatch::guide)
                .findFirst();
    }

    public Optional<StardewData.SpecialOrderGuide> findSpecialOrder(String query) {
        return findSpecialOrders(query).stream().findFirst();
    }

    public Optional<StardewData.FestivalEvent> findFestivalEvent(String query) {
        String q = normalize(query);
        return festivalEvents().stream()
                .map(event -> new FestivalEventMatch(event, scoreFestivalEvent(q, event)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(FestivalEventMatch::event)
                .findFirst();
    }

    public Optional<StardewData.FarmMapGuide> findFarmMap(String query) {
        String q = normalize(query);
        return farmMaps().stream()
                .map(map -> new FarmMapMatch(map, scoreFarmMap(q, map)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(FarmMapMatch::map)
                .findFirst();
    }

    public Optional<StardewData.FarmAnimalGuide> findFarmAnimal(String query) {
        String q = normalize(query);
        return farmAnimals().stream()
                .map(animal -> new FarmAnimalMatch(animal, scoreFarmAnimal(q, animal)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(FarmAnimalMatch::animal)
                .findFirst();
    }

    public List<StardewData.SpecialOrderGuide> findSpecialOrders(String query) {
        String q = normalize(query);
        return specialOrders().stream()
                .map(order -> new SpecialOrderMatch(order, scoreSpecialOrder(q, order)))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(SpecialOrderMatch::order)
                .toList();
    }

    public Optional<StardewData.BookGuide> findBook(String query) {
        return findBooks(query).stream().findFirst();
    }

    public List<StardewData.BookGuide> findBooks(String query) {
        String q = normalize(query);
        return books().stream()
                .map(b -> new BookMatch(b, scoreSearchable(q, b.getName(), b.getNameEn(), b.getAliases())))
                .filter(match -> match.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .map(BookMatch::book)
                .toList();
    }

    public Optional<StardewData.Fish> findFish(String query) {
        String q = normalize(query);
        return fish().stream()
                .filter(f -> matches(q, f.getName(), f.getNameEn(), f.getAliases()))
                .findFirst();
    }

    private boolean matches(String q, String name, String en, List<String> aliases) {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.add(en);
        if (aliases != null) {
            names.addAll(aliases);
        }
        for (String n : names) {
            String normalized = normalize(n);
            if (!normalized.isEmpty() && (q.contains(normalized) || normalized.contains(q))) {
                return true;
            }
        }
        return false;
    }

    private int scoreSearchable(String q, String name, String en, List<String> aliases) {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.add(en);
        if (aliases != null) {
            names.addAll(aliases);
        }
        int score = 0;
        Set<String> seenNames = new LinkedHashSet<>();
        for (String n : names) {
            String normalized = normalize(n);
            if (!seenNames.add(normalized) || normalized.isEmpty()) {
                continue;
            }
            if (q.equals(normalized)) {
                score += 1000 + normalized.length() * 20;
            } else if (normalized.length() < 2) {
                continue;
            } else if (q.contains(normalized)) {
                score += normalized.length() * 20;
            } else if (normalized.contains(q) && q.length() >= 2) {
                score += q.length() * 5;
            }
        }
        return score;
    }

    private int firstSearchableIndex(String q, String name, String en, List<String> aliases) {
        List<String> names = new ArrayList<>();
        names.add(name);
        names.add(en);
        if (aliases != null) {
            names.addAll(aliases);
        }
        int first = Integer.MAX_VALUE;
        for (String n : names) {
            String normalized = normalize(n);
            if (normalized.length() < 2) {
                continue;
            }
            int index = q.indexOf(normalized);
            if (index >= 0 && index < first) {
                first = index;
            }
        }
        return first;
    }

    private int scoreGuide(String q, StardewData.GuideTopic guide) {
        List<String> names = new ArrayList<>();
        names.add(guide.getName());
        names.add(guide.getId());
        if (guide.getAliases() != null) {
            names.addAll(guide.getAliases());
        }
        int score = 0;
        Set<String> seenNames = new LinkedHashSet<>();
        for (String n : names) {
            String normalized = normalize(n);
            if (!seenNames.add(normalized)) {
                continue;
            }
            if (!normalized.isEmpty() && q.contains(normalized)) {
                score += Math.max(40, normalized.length() * 10);
            }
        }
        boolean weakNameMatch = q.length() >= 2 && seenNames.stream()
                .anyMatch(n -> !n.isEmpty() && n.contains(q));
        if (weakNameMatch) {
            score += 20;
        }
        if (score == 0) {
            return 0;
        }
        List<String> keywords = guide.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return score;
        }
        int keywordScore = keywords.stream()
                .map(StardewKnowledgeRepository::normalize)
                .filter(k -> !k.isEmpty() && q.contains(k))
                .mapToInt(k -> Math.max(5, k.length() * 2))
                .sum();
        return score + keywordScore;
    }

    private int scoreSkillGuide(String q, StardewData.SkillGuide guide) {
        List<String> names = new ArrayList<>();
        names.add(guide.getName());
        names.add(guide.getNameEn());
        names.add(guide.getId());
        if (guide.getAliases() != null) {
            names.addAll(guide.getAliases());
        }
        int score = 0;
        Set<String> seenNames = new LinkedHashSet<>();
        for (String n : names) {
            String normalized = normalize(n);
            if (!seenNames.add(normalized)) {
                continue;
            }
            if (!normalized.isEmpty() && q.contains(normalized)) {
                score += Math.max(40, normalized.length() * 10);
            }
        }
        boolean weakNameMatch = q.length() >= 2 && seenNames.stream()
                .anyMatch(n -> !n.isEmpty() && n.contains(q));
        if (weakNameMatch) {
            score += 20;
        }
        if (score == 0) {
            return 0;
        }
        List<String> keywords = guide.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return score;
        }
        int keywordScore = keywords.stream()
                .map(StardewKnowledgeRepository::normalize)
                .filter(k -> !k.isEmpty() && q.contains(k))
                .mapToInt(k -> Math.max(5, k.length() * 2))
                .sum();
        return score + keywordScore;
    }

    private int scoreSpecialOrder(String q, StardewData.SpecialOrderGuide order) {
        int score = scoreSearchable(q, order.getName(), order.getNameEn(), order.getAliases());
        String board = normalize(order.getBoard());
        if (!board.isEmpty() && q.contains(board)) {
            score += 20;
        }
        String requester = normalize(order.getRequester());
        if (!requester.isEmpty() && q.contains(requester)) {
            score += 20;
        }
        if (score == 0) {
            return 0;
        }
        List<String> extra = new ArrayList<>();
        extra.add(order.getPrerequisite());
        extra.add(order.getTimeframe());
        extra.add(order.getRepeatable());
        if (order.getRequirements() != null) {
            extra.addAll(order.getRequirements());
        }
        if (order.getRewards() != null) {
            extra.addAll(order.getRewards());
        }
        if (order.getTips() != null) {
            extra.addAll(order.getTips());
        }
        for (String value : extra) {
            String normalized = normalize(value);
            if (!normalized.isEmpty() && q.contains(normalized)) {
                score += Math.min(30, Math.max(5, normalized.length() / 2));
            }
        }
        return score;
    }

    private int scoreFestivalEvent(String q, StardewData.FestivalEvent event) {
        int score = scoreSearchable(q, event.getName(), event.getNameEn(), event.getAliases());
        if (score == 0) {
            return 0;
        }
        String season = normalize(event.getSeason());
        if (!season.isEmpty() && q.contains(season)) {
            score += 20;
        }
        if (event.getStartDay() != null && q.contains(event.getStartDay() + "日")) {
            score += 20;
        }
        if (event.getStartDay() != null && event.getEndDay() != null
                && event.getEndDay() > event.getStartDay() && q.contains(event.getEndDay() + "日")) {
            score += 10;
        }
        return score;
    }

    private int scoreFarmMap(String q, StardewData.FarmMapGuide map) {
        int score = scoreSearchable(q, map.getName(), map.getNameEn(), map.getAliases());
        if (score == 0) {
            return 0;
        }
        if (map.getAssociatedSkills() != null) {
            for (String skill : map.getAssociatedSkills()) {
                String normalized = normalize(skill);
                if (!normalized.isEmpty() && q.contains(normalized)) {
                    score += 10;
                }
            }
        }
        if (map.getBestFor() != null) {
            for (String value : map.getBestFor()) {
                String normalized = normalize(value);
                if (!normalized.isEmpty() && q.contains(normalized)) {
                    score += 10;
                }
            }
        }
        return score;
    }

    private int scoreFarmAnimal(String q, StardewData.FarmAnimalGuide animal) {
        int score = scoreSearchable(q, animal.getName(), animal.getNameEn(), animal.getAliases());
        if (score == 0 && animal.getProducts() != null) {
            for (StardewData.AnimalProduct product : animal.getProducts()) {
                score = Math.max(score, scoreSearchable(q, product.getName(), product.getProcessedInto(), List.of()));
            }
        }
        if (score == 0) {
            return 0;
        }
        if (animal.getBestFor() != null) {
            for (String value : animal.getBestFor()) {
                String normalized = normalize(value);
                if (!normalized.isEmpty() && q.contains(normalized)) {
                    score += 10;
                }
            }
        }
        if (animal.getBuilding() != null && q.contains(normalize(animal.getBuilding()))) {
            score += 10;
        }
        return score;
    }

    private int scoreBundle(String q, StardewData.Bundle bundle) {
        int score = scoreSearchable(q, bundle.getName(), bundle.getId(), bundle.getAliases());
        boolean asksRemixed = q.contains("重混") || q.contains("随机") || q.contains("remixed");
        if (asksRemixed && (bundle.getId().startsWith("remixed_") || normalize(bundle.getName()).contains("重混"))) {
            score += 500;
        }
        return score;
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("/", "")
                .replace("（", "(")
                .replace("）", ")")
                .trim();
    }

    private record GuideMatch(StardewData.GuideTopic guide, int score) {
    }

    private record SkillGuideMatch(StardewData.SkillGuide guide, int score) {
    }

    private record FestivalEventMatch(StardewData.FestivalEvent event, int score) {
    }

    private record FarmMapMatch(StardewData.FarmMapGuide map, int score) {
    }

    private record FarmAnimalMatch(StardewData.FarmAnimalGuide animal, int score) {
    }

    private record BundleMatch(StardewData.Bundle bundle, int score) {
    }

    private record BuildingMatch(StardewData.Building building, int score) {
    }

    private record ToolMatch(StardewData.Tool tool, int score) {
    }

    private record MachineMatch(StardewData.Machine machine, int score) {
    }

    private record CraftingRecipeMatch(StardewData.CraftingRecipe recipe, int score) {
    }

    private record ShopMatch(StardewData.Shop shop, int score) {
    }

    private record CookingRecipeMatch(StardewData.CookingRecipe recipe, int score) {
    }

    private record BookMatch(StardewData.BookGuide book, int score) {
    }

    private record SpecialOrderMatch(StardewData.SpecialOrderGuide order, int score) {
    }

    private record ResourceMatch(StardewData.ResourceGuide resource, int score, int firstIndex) {
    }

    private record MonsterDropMatch(StardewData.MonsterDropGuide monster, int score) {
    }

    private record FishPondMatch(StardewData.FishPondGuide fishPond, int score) {
    }
}
