package com.bb.bot.handler.stardew;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class StardewGuideRetriever {

    private final StardewGuideService guideService;

    public StardewGuideRetriever(StardewGuideService guideService) {
        this.guideService = guideService;
    }

    public List<StardewGuideEvidence> retrieve(String rawQuery, StardewQueryPlan plan) {
        String query = StringUtils.defaultString(rawQuery).trim();
        StardewQueryPlan effectivePlan = plan;
        if (effectivePlan == null || effectivePlan.getIntents() == null || effectivePlan.getIntents().isEmpty()) {
            effectivePlan = StardewQueryPlan.fallback(query);
        }
        List<StardewGuideEvidence> evidence = new ArrayList<>();
        Set<String> seenQueries = new LinkedHashSet<>();
        for (StardewQueryPlan.PlannedIntent intent : effectivePlan.getIntents()) {
            StardewGuideIntent type = intent.getType() == null ? StardewGuideIntent.UNKNOWN : intent.getType();
            if (type == StardewGuideIntent.UNKNOWN) {
                continue;
            }
            for (String searchQuery : searchQueries(query, intent)) {
                if (!seenQueries.add(type + ":" + searchQuery)) {
                    continue;
                }
                StardewGuideResult result = guideService.answerEvidence(type, searchQuery);
                if (result == null || StringUtils.isBlank(result.getAnswer())) {
                    continue;
                }
                evidence.add(new StardewGuideEvidence(type, searchQuery, result.getIntent(), result.getAnswer()));
            }
        }
        return evidence;
    }

    private List<String> searchQueries(String originalQuery, StardewQueryPlan.PlannedIntent intent) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        List<String> keywords = intent.getKeywords() == null || intent.getKeywords().isEmpty()
                ? List.of(originalQuery)
                : intent.getKeywords();
        for (String keyword : keywords) {
            String cleaned = StringUtils.defaultString(keyword).replace("星露谷物语", "星露谷").trim();
            if (StringUtils.isBlank(cleaned)) {
                continue;
            }
            queries.add(applyTypeHint(cleaned, intent));
        }
        if (queries.isEmpty() && StringUtils.isNotBlank(originalQuery)) {
            queries.add(applyTypeHint(originalQuery, intent));
        }
        return queries.stream().limit(6).toList();
    }

    private String applyTypeHint(String query, StardewQueryPlan.PlannedIntent intent) {
        StardewGuideIntent type = intent.getType() == null ? StardewGuideIntent.UNKNOWN : intent.getType();
        String withConstraints = appendConstraints(query, intent.getConstraints());
        return switch (type) {
            case FISH -> ensureContains(withConstraints, "鱼");
            case BUNDLE -> ensureContains(withConstraints, "收集包");
            case VILLAGER_SCHEDULE -> ensureContains(withConstraints, "在哪");
            case VILLAGER_PROFILE -> ensureContains(withConstraints, "礼物");
            case RESOURCE -> ensureResourceAction(withConstraints);
            case MONSTER_DROP -> ensureContains(withConstraints, "掉什么");
            case FISH_POND -> ensureContains(withConstraints, "鱼塘");
            case ANIMAL_CARE -> ensureContains(withConstraints, "动物养殖");
            case FRUIT_TREE -> ensureContains(withConstraints, "果树");
            case CROP -> ensureContains(withConstraints, "作物");
            case TOOL -> ensureContains(withConstraints, "工具");
            case BUILDING -> ensureContains(withConstraints, "建筑");
            case CRAFTING -> ensureContains(withConstraints, "制作配方");
            case MACHINE -> ensureContains(withConstraints, "机器");
            case SHOP -> ensureContains(withConstraints, "在哪里买");
            case COOKING -> ensureContains(withConstraints, "料理");
            case QUEST -> ensureContains(withConstraints, "任务");
            case SPECIAL_ORDER -> ensureContains(withConstraints, "特别订单");
            case SKILL -> ensureContains(withConstraints, "技能");
            case FESTIVAL -> ensureContains(withConstraints, "节日");
            case FARM_MAP -> ensureContains(withConstraints, "农场地图");
            case ISLAND -> ensureContains(withConstraints, "姜岛探索");
            case DUNGEON -> ensureContains(withConstraints, "地下城攻略");
            case MUSEUM -> ensureContains(withConstraints, "博物馆");
            case GUIDE -> ensureGuideHint(withConstraints);
            case UNKNOWN -> withConstraints;
        };
    }

    private String ensureGuideHint(String query) {
        if (containsAny(query, "小饰品", "饰品", "铁砧", "重铸", "仙女盒", "青蛙蛋", "寒冰法杖",
                "黄金马刺", "魔法箭筒", "鹦鹉蛋", "蜥怪的爪子", "魔法发胶", "trinket")) {
            return ensureContains(query, "饰品攻略");
        }
        if (containsAny(query, "精通", "精通点", "精通洞穴", "铱金镰刀", "祝福雕像", "金色动物饼干",
                "矮人之王雕像", "重型熔炉", "神秘树种子", "宝藏图腾", "高级铱金鱼竿",
                "挑战鱼饵", "金色钓鱼宝箱", "迷你锻造台", "mastery")) {
            return ensureContains(query, "精通系统");
        }
        if (containsAny(query, "锻造", "附魔", "火山晶石", "银河之魂", "无限武器", "戒指合成",
                "forge", "enchant", "infinity weapon")) {
            return ensureContains(query, "火山锻造");
        }
        return query;
    }

    private String appendConstraints(String query, StardewQueryPlan.StardewQueryConstraints constraints) {
        if (constraints == null) {
            return query;
        }
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        addIfMissing(parts, query, constraints.getSeason());
        addIfMissing(parts, query, constraints.getLocation());
        addIfMissing(parts, query, constraints.getWeather());
        addIfMissing(parts, query, constraints.getTime());
        addIfMissing(parts, query, constraints.getDay());
        addIfMissing(parts, query, constraints.getWeekday());
        addIfMissing(parts, query, constraints.getVillager());
        if (parts.isEmpty()) {
            return query;
        }
        return query + " " + String.join(" ", parts);
    }

    private void addIfMissing(Set<String> parts, String query, String value) {
        String cleaned = StringUtils.defaultString(value).trim();
        if (StringUtils.isNotBlank(cleaned) && !query.contains(cleaned)) {
            parts.add(cleaned);
        }
    }

    private String ensureContains(String query, String hint) {
        return query.contains(hint) ? query : query + " " + hint;
    }

    private String ensureResourceAction(String query) {
        if (query.contains("怎么") || query.contains("获得") || query.contains("获取")
                || query.contains("哪里") || query.contains("刷") || query.contains("来源")
                || query.contains("做")) {
            return query;
        }
        return query + " 怎么获得";
    }

    private boolean containsAny(String query, String... values) {
        for (String value : values) {
            if (query.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
