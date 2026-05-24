package com.bb.bot.handler.pokemon.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.handler.pokemon.entity.PokemonData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 宝可梦核心数据集合 + 选择 / 配对逻辑。从 handler 抽出 IO 与决策，handler 只负责消息。
 *
 * @author ren
 */
@Slf4j
@Component
public class PokemonEngine {

    @Autowired
    private ResourcesUtils resourcesUtils;

    @Getter
    private List<PokemonData> all = Collections.emptyList();
    @Getter
    private List<PokemonData> beforeNames = Collections.emptyList();
    @Getter
    private List<PokemonData> afterNames = Collections.emptyList();

    @PostConstruct
    public void load() {
        try {
            all = parseList("pokemon/pokemon_data.json");
            beforeNames = parseList("pokemon/before_name.json");
            afterNames = parseList("pokemon/after_name.json");
        } catch (Exception e) {
            log.warn("Failed to load pokemon data; pokemon handler will be inactive", e);
        }
    }

    private List<PokemonData> parseList(String path) throws Exception {
        byte[] raw = resourcesUtils.getStaticResourceToByte(path);
        List<PokemonData> parsed = JSON.parseObject(new String(raw, StandardCharsets.UTF_8),
                new TypeReference<List<PokemonData>>() {});
        return parsed == null ? Collections.emptyList() : parsed;
    }

    public boolean isAvailable() {
        return !all.isEmpty();
    }

    /**
     * 抓取一只随机宝可梦。返回 {@code Outcome.captured(index)} 或
     * {@code Outcome.full(currentCount)} 当背包已满（>= maxOwned）。
     */
    public Outcome capture(List<Integer> currentCollection, int maxOwned, RandomGenerator random) {
        if (currentCollection.size() >= maxOwned) {
            return Outcome.full(currentCollection.size());
        }
        if (all.isEmpty()) {
            return Outcome.unavailable();
        }
        int idx = random.nextInt(all.size());
        List<Integer> updated = new ArrayList<>(currentCollection);
        updated.add(idx);
        return Outcome.captured(updated, all.get(idx));
    }

    /**
     * 杂交两只宝可梦。返回失败或合成结果。失败时不修改现有 collection；成功时清空。
     */
    public Outcome breed(List<Integer> currentCollection) {
        if (currentCollection.size() != 2) {
            return Outcome.notEnough();
        }
        if (beforeNames.isEmpty() || afterNames.isEmpty()) {
            return Outcome.unavailable();
        }
        int id1 = currentCollection.get(0);
        int id2 = currentCollection.get(1);
        if (id1 >= beforeNames.size() || id2 >= afterNames.size()) {
            return Outcome.unavailable();
        }
        return Outcome.bred(beforeNames.get(id1), afterNames.get(id2));
    }

    @Getter
    public static class Outcome {
        public enum Type { CAPTURED, FULL, BRED, NOT_ENOUGH, UNAVAILABLE }
        private final Type type;
        private final List<Integer> updatedCollection;
        private final PokemonData captured;
        private final PokemonData breedFrom;
        private final PokemonData breedTo;
        private final int countWhenFull;

        private Outcome(Type type, List<Integer> updated, PokemonData captured,
                        PokemonData from, PokemonData to, int count) {
            this.type = type;
            this.updatedCollection = updated;
            this.captured = captured;
            this.breedFrom = from;
            this.breedTo = to;
            this.countWhenFull = count;
        }

        public static Outcome captured(List<Integer> updated, PokemonData data) {
            return new Outcome(Type.CAPTURED, updated, data, null, null, 0);
        }
        public static Outcome full(int count) {
            return new Outcome(Type.FULL, null, null, null, null, count);
        }
        public static Outcome bred(PokemonData before, PokemonData after) {
            return new Outcome(Type.BRED, Collections.emptyList(), null, before, after, 0);
        }
        public static Outcome notEnough() {
            return new Outcome(Type.NOT_ENOUGH, null, null, null, null, 0);
        }
        public static Outcome unavailable() {
            return new Outcome(Type.UNAVAILABLE, null, null, null, null, 0);
        }
    }
}
