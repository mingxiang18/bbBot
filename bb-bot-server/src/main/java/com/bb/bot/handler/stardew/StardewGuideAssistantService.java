package com.bb.bot.handler.stardew;

import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StardewGuideAssistantService {

    private final StardewGuideService guideService;
    private final StardewQueryPlannerService plannerService;
    private final StardewGuideRetriever retriever;
    private final AiChatService aiChatService;

    public StardewGuideAssistantService(
            StardewGuideService guideService,
            StardewQueryPlannerService plannerService,
            StardewGuideRetriever retriever,
            AiChatService aiChatService
    ) {
        this.guideService = guideService;
        this.plannerService = plannerService;
        this.retriever = retriever;
        this.aiChatService = aiChatService;
    }

    public String answer(String rawQuery) {
        String query = StringUtils.defaultString(rawQuery).trim();
        if (StringUtils.isBlank(query)) {
            return guideService.helpAnswer().getAnswer();
        }

        StardewQueryPlan plan = plannerService.plan(query);
        if (plan.isNeedMoreInfo() && StringUtils.isNotBlank(plan.getClarificationQuestion())) {
            return plan.getClarificationQuestion().trim();
        }

        List<StardewGuideEvidence> evidenceItems = retriever.retrieve(query, plan);
        String evidence = formatEvidence(evidenceItems);
        if (StringUtils.isBlank(evidence)) {
            return noReliableEvidenceAnswer();
        }

        String finalAnswer = synthesizeAnswer(query, evidence);
        if (StringUtils.isNotBlank(finalAnswer)) {
            return finalAnswer.trim();
        }
        String evidenceAnswer = firstEvidenceAnswer(evidenceItems);
        if (StringUtils.isNotBlank(evidenceAnswer)) {
            return evidenceAnswer;
        }
        return noReliableEvidenceAnswer();
    }

    private String formatEvidence(List<StardewGuideEvidence> evidenceItems) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (StardewGuideEvidence evidence : evidenceItems) {
            if (evidence == null || StringUtils.isBlank(evidence.answer())) {
                continue;
            }
            sb.append("资料 ").append(index++).append("\n")
                    .append("类型：").append(evidence.type()).append("\n")
                    .append("查询：").append(evidence.query()).append("\n")
                    .append("命中：").append(evidence.intent()).append("\n")
                    .append(evidence.answer()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String firstEvidenceAnswer(List<StardewGuideEvidence> evidenceItems) {
        if (evidenceItems == null) {
            return null;
        }
        return evidenceItems.stream()
                .filter(evidence -> evidence != null && StringUtils.isNotBlank(evidence.answer()))
                .map(evidence -> evidence.answer().trim())
                .findFirst()
                .orElse(null);
    }

    private String noReliableEvidenceAnswer() {
        return "这个问题我还没有查到足够确定的攻略内容。可以补充具体物品、居民、地点、季节或游戏内时间后再问。";
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
