package com.bb.bot.handler.aiChat;

import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.common.util.aiChat.MessageBuilder;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelRouter;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.common.util.aiChat.provider.VisionDescriber;
import com.bb.bot.constant.BbSendMessageType;
import com.bb.bot.database.chatHistory.entity.ChatHistory;
import com.bb.bot.entity.bb.BbMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class AiToolRouter {

    static final String TOOL_ROUTING_PROMPT =
            "你是工具路由器，只判断最新一条用户消息是否需要工具。"
                    + "历史只用于理解省略、代词、追问和领域延续；只处理最新消息，不要重复处理历史消息。"
                    + "如果最新消息是上一轮同一领域的自然追问，应继续使用相同领域工具；"
                    + "如果最新消息明显换话题，不要继承历史领域。";

    private static final String STARDEW_TOOL = "stardew_guide";
    private static final int DOMAIN_HISTORY_SCAN_LIMIT = 8;

    @Autowired
    private ModelRouter modelRouter;

    @Autowired
    private AiToolRegistry toolRegistry;

    @Autowired
    private VisionDescriber visionDescriber;

    /** 领域工具优先，其次区分复杂任务、仅识图和普通聊天。 */
    public ToolRouteDecision decide(AiChatTurnContext context, boolean toolsEnabled) {
        if (context == null) {
            return ToolRouteDecision.none(ModelTier.LIGHT, "NO_CONTEXT");
        }

        ToolRouteDecision selected = selectedDomainTool(context, toolsEnabled);
        if (selected.getMode() == ToolRouteMode.SELECTED_TOOLS) {
            return selected;
        }

        ModelTier tier = context.isDirectTrigger()
                ? modelRouter.classify(buildRoutingMessages(context))
                : ModelTier.LIGHT;
        boolean useAllTools = toolsEnabled && tier == ModelTier.CHAT && context.isDirectTrigger();
        if (useAllTools) {
            return ToolRouteDecision.allTools("DIRECT_COMPLEX");
        }

        if (visionDescriber.enabled() && hasImageRef(context.currentContent(), context.historyList())) {
            return ToolRouteDecision.imageOnly(tier, "IMAGE_REF");
        }
        return ToolRouteDecision.none(tier, context.isDirectTrigger() ? "DIRECT_SIMPLE" : "AUTO_REPLY_LIGHT");
    }

    List<ChatMessage> buildRoutingMessages(AiChatTurnContext context) {
        return MessageBuilder.buildContextMessages(
                TOOL_ROUTING_PROMPT, context.currentContent(), context.historyList(), context.getReplyTarget());
    }

    /** 明确的领域问题及同领域追问只开放对应工具，避免每次挂载全部工具。 */
    private ToolRouteDecision selectedDomainTool(AiChatTurnContext context, boolean toolsEnabled) {
        if (!toolsEnabled) {
            return ToolRouteDecision.none(ModelTier.LIGHT, "TOOLS_DISABLED");
        }
        String current = StringUtils.defaultString(context.getCurrentText());
        boolean explicitStardew = containsStardewMarker(current);
        boolean stardewContinuation = !explicitStardew
                && recentHistoryContainsStardew(context.historyList())
                && looksLikeFollowUp(current)
                && !looksLikeTopicSwitch(current);
        if (!explicitStardew && !stardewContinuation) {
            return ToolRouteDecision.none(ModelTier.LIGHT, "NO_SELECTED_DOMAIN");
        }
        if (toolRegistry.get(STARDEW_TOOL) == null) {
            log.warn("上下文路由命中星露谷，但工具未注册 tool={}", STARDEW_TOOL);
            return ToolRouteDecision.none(ModelTier.CHAT, "SELECTED_TOOL_MISSING:" + STARDEW_TOOL);
        }
        return ToolRouteDecision.selectedTools(List.of(STARDEW_TOOL),
                explicitStardew ? "STARDEW_EXPLICIT" : "STARDEW_CONTEXT_FOLLOWUP");
    }

    private boolean recentHistoryContainsStardew(List<ChatHistory> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        int seen = 0;
        for (int i = history.size() - 1; i >= 0 && seen < DOMAIN_HISTORY_SCAN_LIMIT; i--, seen++) {
            if (containsStardewMarker(MessageBuilder.extractTextContent(history.get(i)))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsStardewMarker(String text) {
        String normalized = normalize(text);
        return normalized.contains("星露谷")
                || normalized.contains("stardew")
                || normalized.contains("玛妮")
                || normalized.contains("marnie");
    }

    private boolean looksLikeFollowUp(String text) {
        String t = normalize(text);
        if (StringUtils.isBlank(t)) {
            return false;
        }
        if (t.length() <= 36) {
            return containsAny(t, "哪个", "哪里", "哪儿", "怎么", "多少", "几", "要", "能不能",
                    "可以", "好做", "获得", "获取", "买", "做", "送", "喜欢", "最爱", "礼物",
                    "材料", "价格", "任务", "这个", "那个", "它", "他", "她");
        }
        return containsAny(t, "这个", "那个", "它", "他", "她", "上面", "刚才", "继续");
    }

    /** 新问题已切换话题时，不继承历史中提到的星露谷工具。 */
    private boolean looksLikeTopicSwitch(String text) {
        String t = normalize(text);
        return containsAny(t, "天气", "新闻", "热搜", "股价", "股票", "汇率", "日程", "splatoon",
                "喷射", "打工", "真格", "代码", "文件", "服务器", "k8s", "kubernetes");
    }

    static boolean hasImageRef(List<BbMessageContent> current, List<ChatHistory> history) {
        if (current != null) {
            for (BbMessageContent c : current) {
                if (BbSendMessageType.NET_IMAGE.equals(c.getType())) {
                    return true;
                }
            }
        }
        if (history != null) {
            int seen = 0;
            for (int i = history.size() - 1; i >= 0 && seen < 8; i--, seen++) {
                String t = history.get(i).getText();
                if (t != null && (t.contains("netImage") || t.contains("图片 ref="))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return StringUtils.defaultString(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
