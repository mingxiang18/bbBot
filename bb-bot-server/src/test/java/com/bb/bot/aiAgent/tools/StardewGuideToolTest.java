package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.handler.stardew.StardewGuideAssistantService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StardewGuideToolTest {

    @Test
    void keepsToolDescriptionFocusedOnToolSelectionNotInternalRouting() throws NoSuchMethodException {
        Method guide = StardewGuideTool.class.getMethod("guide", String.class);
        AiTool aiTool = guide.getAnnotation(AiTool.class);

        assertThat(aiTool.name()).isEqualTo("stardew_guide");
        assertThat(aiTool.description()).contains("星露谷物语攻略问题", "保留用户原问题");
        assertThat(aiTool.description().length()).isLessThan(80);
        assertThat(aiTool.description())
                .doesNotContain("分类", "检索", "自然语言整合", "夏天能钓什么鱼", "全 42 古物", "全 53 矿物", "煤尘精灵", "铁砧重铸");
    }

    @Test
    void returnsAssistantAnswerThroughSharedRetrievalPath() {
        StardewGuideAssistantService assistantService = mock(StardewGuideAssistantService.class);
        StardewGuideTool tool = new StardewGuideTool(assistantService);
        when(assistantService.answer("战斗等级低怎么快速升级"))
                .thenReturn("战斗经验来自击杀怪物，前期可以刷矿井高密度层，搭配更好的武器和食物。");

        Map<String, Object> result = tool.guide("战斗等级低怎么快速升级");

        verify(assistantService).answer("战斗等级低怎么快速升级");
        assertThat(result).containsEntry("query", "战斗等级低怎么快速升级");
        assertThat(result).containsEntry("intent", "ai_synthesized");
        assertThat(result.get("answer").toString()).contains("战斗经验", "击杀怪物");
        assertThat(result.get("evidence").toString()).contains("战斗经验", "击杀怪物");
        assertThat(result.get("replyInstruction").toString()).contains("直接基于 answer", "不要声称读取了用户存档");
        assertThat(result).doesNotContainKeys("sourceUrls", "gameVersion", "lastCheckedAt", "note");
    }
}
