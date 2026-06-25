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
                        - 保留季节、地点、天气、时间、居民名、物品名、建筑名、收集包名。
                        - 缺少居民位置查询必需的游戏内时间时，needMoreInfo=true，并给 clarificationQuestion。
                        - 不要声称读取存档或当前真实游戏状态。
                        """),
                ChatMessage.user(cleanedQuery)
        ), ModelTier.LIGHT);
        StardewQueryPlan plan = parsePlan(raw);
        if (plan == null || plan.getIntents() == null || plan.getIntents().isEmpty()) {
            return StardewQueryPlan.fallback(cleanedQuery);
        }
        for (StardewQueryPlan.PlannedIntent intent : plan.getIntents()) {
            if (intent.getType() == null) {
                intent.setType(StardewGuideIntent.UNKNOWN);
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
