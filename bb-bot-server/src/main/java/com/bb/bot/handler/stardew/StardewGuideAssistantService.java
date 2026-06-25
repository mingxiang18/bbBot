package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StardewGuideAssistantService {

    private static final Pattern JSON_STRING = Pattern.compile("\"([^\"]+)\"");

    private final StardewGuideService guideService;
    private final AiChatService aiChatService;

    public StardewGuideAssistantService(StardewGuideService guideService, AiChatService aiChatService) {
        this.guideService = guideService;
        this.aiChatService = aiChatService;
    }

    public String answer(String rawQuery) {
        String query = StringUtils.defaultString(rawQuery).trim();
        if (StringUtils.isBlank(query)) {
            return guideService.answer(query).getAnswer();
        }

        List<String> searchQueries = expandSearchQueries(query);
        String evidence = collectEvidence(searchQueries);
        if (StringUtils.isBlank(evidence)) {
            return guideService.answer(query).getAnswer();
        }

        String finalAnswer = synthesizeAnswer(query, evidence);
        if (StringUtils.isNotBlank(finalAnswer)) {
            return finalAnswer.trim();
        }
        return guideService.answer(query).getAnswer();
    }

    private List<String> expandSearchQueries(String query) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("""
                        你是星露谷物语攻略检索关键词生成器。
                        任务：把用户问题拆成 1-5 个适合查询攻略资料的中文关键词或短句。
                        要求：
                        - 保留季节、地点、天气、时间、居民名、物品名、建筑名等条件。
                        - 获取、制作、升级、购买、位置、收集包等问题要保留动作，例如“怎么获得”“怎么做”“升级材料”“在哪里”。
                        - 可以补充同义词或更核心的页面名。
                        - 只输出 JSON 字符串数组，不要解释。
                        """),
                ChatMessage.user(query)
        );
        String raw = chatSafely(messages, ModelTier.LIGHT);
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(query);
        queries.addAll(parseQueries(raw));
        return queries.stream().limit(6).toList();
    }

    private List<String> parseQueries(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        Set<String> queries = new LinkedHashSet<>();
        Matcher matcher = JSON_STRING.matcher(raw);
        while (matcher.find()) {
            addQuery(queries, matcher.group(1));
        }
        if (!queries.isEmpty()) {
            return new ArrayList<>(queries);
        }
        for (String line : raw.split("\\R")) {
            String cleaned = line
                    .replaceFirst("^\\s*[-*]\\s*", "")
                    .replaceFirst("^\\s*\\d+[.)、]\\s*", "")
                    .trim();
            addQuery(queries, cleaned);
        }
        return new ArrayList<>(queries);
    }

    private void addQuery(Set<String> queries, String query) {
        String cleaned = StringUtils.defaultString(query)
                .replace("星露谷物语", "星露谷")
                .trim();
        if (StringUtils.isNotBlank(cleaned)) {
            queries.add(cleaned);
        }
    }

    private String collectEvidence(List<String> searchQueries) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (String searchQuery : searchQueries) {
            StardewGuideResult result = guideService.answer(searchQuery);
            if (result == null || StringUtils.isBlank(result.getAnswer())) {
                continue;
            }
            sb.append("证据 ").append(index++).append("，查询：").append(searchQuery).append("\n")
                    .append(result.getAnswer()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String synthesizeAnswer(String query, String evidence) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("""
                        你是星露谷物语攻略助手。
                        请只根据给定证据回答用户问题，必要时说明缺少哪些游戏内条件。
                        回复要求：
                        - 中文，自然、简洁、可执行。
                        - 不要提“证据”“关键词”“数据版本”“校验日期”“来源链接”“Wiki”“本地库”。
                        - 不要声称读取了用户存档。
                        - 如果证据之间有冲突，给出保守建议并提示用户补充条件。
                        """),
                ChatMessage.user("用户问题：\n" + query + "\n\n检索到的资料：\n" + evidence)
        );
        return chatSafely(messages, ModelTier.CHAT);
    }

    private String chatSafely(List<ChatMessage> messages, ModelTier tier) {
        try {
            return aiChatService.chat(messages, tier);
        } catch (Exception ignored) {
            return null;
        }
    }
}
