package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.handler.stardew.StardewGuideAssistantService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class StardewGuideTool {

    private final StardewGuideAssistantService guideAssistantService;

    public StardewGuideTool(StardewGuideAssistantService guideAssistantService) {
        this.guideAssistantService = guideAssistantService;
    }

    @AiTool(
            name = "stardew_guide",
            description = "查询星露谷物语攻略问题时调用，保留用户原问题。"
    )
    public Map<String, Object> guide(
            @AiToolParam(name = "query", description = "用户的星露谷攻略问题，保留季节、日期、时间、天气等条件")
            String query
    ) {
        String answer = guideAssistantService.answer(query);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("intent", "ai_synthesized");
        out.put("answer", answer);
        out.put("evidence", answer);
        out.put("replyInstruction", "请直接基于 answer 回复用户；不要提数据版本、校验日期、来源链接或 Wiki；不要声称读取了用户存档。");
        return out;
    }
}
