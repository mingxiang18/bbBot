package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StardewQueryPlannerService {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StardewQueryPlannerService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public StardewQueryPlan plan(String query) {
        String cleanedQuery = StringUtils.defaultString(query).trim();
        if (StringUtils.isBlank(cleanedQuery)) {
            return StardewQueryPlan.fallback(cleanedQuery);
        }
        String raw = chatSafely(List.of(
                ChatMessage.system("""
                        你是星露谷物语攻略查询规划器，只负责把用户问题拆成检索计划，不负责回答。
                        必须只输出 JSON，不要解释，不要 Markdown。
                        JSON 结构：
                        {
                          "needMoreInfo": false,
                          "clarificationQuestion": "",
                          "intents": [
                            {
                              "type": "RESOURCE",
                              "keywords": ["恐龙蛋怎么获得"],
                              "constraints": {
                                "season": "", "location": "", "weather": "", "time": "",
                                "day": "", "weekday": "", "villager": ""
                              }
                            }
                          ]
                        }
                        type 只能从这些枚举中选择：
                        FISH, BUNDLE, VILLAGER_SCHEDULE, VILLAGER_PROFILE, RESOURCE,
                        ANIMAL_CARE, FRUIT_TREE, CROP, TOOL, BUILDING, MACHINE, SHOP,
                        COOKING, SKILL, MUSEUM, GUIDE, UNKNOWN。
                        规划规则：
                        - 可以拆成 1-4 个 intent；组合问题要拆开，例如“动物怎么养，大壶牛奶为什么不出”拆 ANIMAL_CARE + RESOURCE。
                        - keywords 必须是适合检索的中文短句，保留动作，例如“怎么获得”“怎么做”“升级材料”“在哪里”“怎么种”。
                        - 技能等级怎么升、快速升级、职业怎么选、战斗/采矿/钓鱼/耕种/觅食经验路线归为 SKILL。
                        - 保留季节、地点、天气、时间、居民名、物品名、建筑名、收集包名。
                        - 缺少居民位置查询必需的游戏内时间时，needMoreInfo=true，并给 clarificationQuestion。
                        - 不要声称读取存档或当前真实游戏状态。
                        """),
                ChatMessage.user(cleanedQuery)
        ), ModelTier.LIGHT);
        StardewQueryPlan plan = parsePlan(raw);
        if (plan == null || plan.getIntents() == null || plan.getIntents().isEmpty()) {
            return localFallbackPlan(cleanedQuery);
        }
        for (StardewQueryPlan.PlannedIntent intent : plan.getIntents()) {
            if (intent.getType() == null) {
                String keyword = intent.getKeywords() == null || intent.getKeywords().isEmpty()
                        ? cleanedQuery
                        : intent.getKeywords().get(0);
                intent.setType(inferIntent(keyword));
            }
            if (intent.getKeywords() == null || intent.getKeywords().isEmpty()) {
                intent.setKeywords(List.of(cleanedQuery));
            }
            if (intent.getConstraints() == null) {
                intent.setConstraints(new StardewQueryPlan.StardewQueryConstraints());
            }
        }
        return plan;
    }

    private StardewQueryPlan localFallbackPlan(String query) {
        StardewQueryPlan plan = new StardewQueryPlan();
        StardewQueryPlan.PlannedIntent intent = new StardewQueryPlan.PlannedIntent();
        intent.setType(inferIntent(query));
        intent.setKeywords(List.of(query));
        plan.setIntents(List.of(intent));
        return plan;
    }

    private StardewGuideIntent inferIntent(String query) {
        String q = StringUtils.defaultString(query).trim();
        if (StringUtils.isBlank(q)) {
            return StardewGuideIntent.UNKNOWN;
        }
        if (containsAny(q, "收集包", "献祭", "社区中心", "电影院", "失踪的包")) {
            return StardewGuideIntent.BUNDLE;
        }
        if (containsAny(q, "博物馆", "捐赠", "古物", "矿物", "卷轴")) {
            return StardewGuideIntent.MUSEUM;
        }
        if (containsAny(q, "在哪", "位置", "日程", "行程", "几点")
                && !containsAny(q, "哪里买", "在哪里买", "怎么获得", "哪里刷", "在哪刷")) {
            return StardewGuideIntent.VILLAGER_SCHEDULE;
        }
        if (containsAny(q, "喜欢", "讨厌", "礼物", "生日", "红心", "好感")) {
            return StardewGuideIntent.VILLAGER_PROFILE;
        }
        if (containsAny(q, "斧头", "镐", "锄头", "水壶", "垃圾桶", "工具升级")
                && containsAny(q, "升级", "多少钱", "材料", "需要", "条件")) {
            return StardewGuideIntent.TOOL;
        }
        if (containsAny(q, "鸡舍", "畜棚", "筒仓", "马厩", "鱼塘", "史莱姆屋", "方尖塔", "黄金钟", "房屋升级", "社区升级", "罗宾")
                || (containsAny(q, "建筑", "建造") && containsAny(q, "材料", "需要", "多少钱", "升级"))) {
            return StardewGuideIntent.BUILDING;
        }
        if (containsAny(q, "技能", "战斗", "采矿", "耕种", "觅食", "钓鱼")
                && containsAny(q, "等级", "升级", "经验", "怎么练", "快速", "职业")) {
            return StardewGuideIntent.SKILL;
        }
        if (looksLikeBookQuery(q) && containsAny(q, "在哪里买", "哪里买", "谁卖", "多少钱", "购买")) {
            return StardewGuideIntent.SHOP;
        }
        if (looksLikeBookQuery(q)) {
            return StardewGuideIntent.GUIDE;
        }
        if (containsAny(q, "果树", "树苗", "温室怎么种")) {
            return StardewGuideIntent.FRUIT_TREE;
        }
        if (containsAny(q, "动物", "奶牛", "山羊", "鸡", "鸭", "兔子", "恐龙", "猪", "心情", "大壶奶")) {
            return StardewGuideIntent.ANIMAL_CARE;
        }
        if (containsAny(q, "buff", "增益", "料理", "食物", "饮料")
                && containsAny(q, "叠加", "覆盖", "规则", "机制", "能一起", "能同时", "互相覆盖")) {
            return StardewGuideIntent.GUIDE;
        }
        if (looksLikeCookingFoodQuery(q) && containsAny(q, "怎么做", "材料", "配方", "效果", "吃什么", "有哪些")) {
            return StardewGuideIntent.COOKING;
        }
        if (containsAny(q, "料理", "食谱", "菜谱", "烹饪")) {
            return StardewGuideIntent.COOKING;
        }
        if (containsAny(q, "商店", "哪里买", "在哪里买", "谁卖", "价格", "多少钱")
                && !containsAny(q, "升级多少钱")) {
            return StardewGuideIntent.SHOP;
        }
        if (containsAny(q, "洒水器", "小桶", "罐头瓶", "蛋黄酱机", "奶酪机", "织布机", "熔炉", "避雷针", "回收机", "晶球破开器", "楼梯", "鱼饵", "浮标", "戒指", "图腾")
                && containsAny(q, "怎么做", "材料", "配方", "制作", "需要")) {
            return StardewGuideIntent.MACHINE;
        }
        if (containsAny(q, "作物", "种什么", "种子", "几天成熟", "收益", "春季", "夏季", "秋季", "冬季")
                && !containsAny(q, "钓", "鱼")) {
            return StardewGuideIntent.CROP;
        }
        if (containsAny(q, "钓", "鱼", "蟹笼", "果冻")) {
            return StardewGuideIntent.FISH;
        }
        if (containsAny(q, "怎么获得", "获取", "哪里刷", "在哪刷", "来源", "掉落", "怎么弄", "怎么拿", "哪里有")) {
            return StardewGuideIntent.RESOURCE;
        }
        if (containsAny(q, "攻略", "推荐", "路线", "怎么玩", "怎么解锁")) {
            return StardewGuideIntent.GUIDE;
        }
        return StardewGuideIntent.UNKNOWN;
    }

    private boolean looksLikeBookQuery(String query) {
        return containsAny(query,
                "技能书", "书商", "书籍", "力量书", "能力书", "永久能力书",
                "星之书", "价格目录", "风之道", "马之书", "老滑腿", "酱料女皇食谱",
                "鱼饵和浮标", "采矿月刊", "战斗季刊", "樵夫周刊", "星露谷年鉴",
                "怪物图鉴", "洞穴系统地图", "矮人安全手册", "海之宝石", "捕蟹的艺术",
                "伍迪的秘密", "浣熊日志", "神秘之书", "友谊 101", "宝物鉴定指南",
                "Book Of Stars", "Price Catalogue", "Way Of The Wind", "Horse: The Book",
                "Queen Of Sauce Cookbook");
    }

    private boolean looksLikeCookingFoodQuery(String query) {
        return containsAny(query,
                "蛋糕", "汤", "沙拉", "披萨", "寿司", "生鱼片", "煎蛋", "蛋卷",
                "早餐", "面包", "薄煎饼", "薯饼", "鱼肉卷", "烤鱼", "炸鱿鱼",
                "蘑菇", "火锅", "山药", "玉米饼", "鳟鱼汤", "鲤鱼惊喜");
    }

    private boolean containsAny(String query, String... words) {
        for (String word : words) {
            if (query.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private StardewQueryPlan parsePlan(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String json = raw.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, StardewQueryPlan.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String chatSafely(List<ChatMessage> messages, ModelTier tier) {
        try {
            return aiChatService.chat(messages, tier);
        } catch (Exception ignored) {
            return null;
        }
    }
}
