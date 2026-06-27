package com.bb.bot.handler.aiChat;

import com.bb.bot.common.util.aiChat.provider.ModelTier;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ToolRouteDecision {

    private final ToolRouteMode mode;
    private final ModelTier tier;
    private final List<String> toolNames;
    private final String reason;

    private ToolRouteDecision(ToolRouteMode mode, ModelTier tier, List<String> toolNames, String reason) {
        this.mode = mode;
        this.tier = tier;
        this.toolNames = toolNames == null ? Collections.emptyList() : List.copyOf(toolNames);
        this.reason = reason;
    }

    public static ToolRouteDecision none(ModelTier tier, String reason) {
        return new ToolRouteDecision(ToolRouteMode.NONE, tier, Collections.emptyList(), reason);
    }

    public static ToolRouteDecision allTools(String reason) {
        return new ToolRouteDecision(ToolRouteMode.ALL_TOOLS, ModelTier.CHAT, Collections.emptyList(), reason);
    }

    public static ToolRouteDecision selectedTools(List<String> toolNames, String reason) {
        return new ToolRouteDecision(ToolRouteMode.SELECTED_TOOLS, ModelTier.CHAT, toolNames, reason);
    }

    public static ToolRouteDecision imageOnly(ModelTier tier, String reason) {
        return new ToolRouteDecision(ToolRouteMode.IMAGE_ONLY, tier, List.of("analyze_image"), reason);
    }

    public boolean usesTools() {
        return mode == ToolRouteMode.ALL_TOOLS
                || mode == ToolRouteMode.SELECTED_TOOLS
                || mode == ToolRouteMode.IMAGE_ONLY;
    }
}
