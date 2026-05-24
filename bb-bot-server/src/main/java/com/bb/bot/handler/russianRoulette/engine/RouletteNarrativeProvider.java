package com.bb.bot.handler.russianRoulette.engine;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.ResourcesUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 把俄罗斯轮盘的死亡 / 存活叙事文本从代码里挪到 {@code static/russianRoulette/narratives.json}，
 * 启动时一次性读入；handler 通过本组件取一条随机文本。
 *
 * @author ren
 */
@Slf4j
@Component
public class RouletteNarrativeProvider {

    @Autowired
    private ResourcesUtils resourcesUtils;

    private List<String> deathLines = new ArrayList<>();
    private List<String> surviveLines = new ArrayList<>();

    @PostConstruct
    public void load() {
        try {
            byte[] raw = resourcesUtils.getStaticResourceToByte("russianRoulette/narratives.json");
            JSONObject obj = JSON.parseObject(new String(raw, StandardCharsets.UTF_8));
            deathLines = obj.getList("death", String.class);
            surviveLines = obj.getList("survive", String.class);
            if (deathLines == null) deathLines = new ArrayList<>();
            if (surviveLines == null) surviveLines = new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to load russian roulette narratives, falling back to empty list", e);
            deathLines = Collections.emptyList();
            surviveLines = Collections.emptyList();
        }
    }

    public String randomDeath(RandomGenerator random) {
        return pick(deathLines, random, "砰！游戏结束。");
    }

    public String randomSurvive(RandomGenerator random) {
        return pick(surviveLines, random, "咔哒，子弹未响。");
    }

    /** 仅供测试覆盖文本时使用。 */
    void setLines(List<String> deathLines, List<String> surviveLines) {
        this.deathLines = deathLines;
        this.surviveLines = surviveLines;
    }

    private static String pick(List<String> source, RandomGenerator random, String fallback) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        return source.get(random.nextInt(source.size()));
    }
}
