package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.memory.ExperienceStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RecallExperienceTool {

    @Autowired
    private ExperienceStore experienceStore;

    @AiTool(
            name = "recall_experience",
            description = "查看分类经验笔记。不传 category 列出所有 categories；" +
                    "传 category 读该分类下的所有条目。" +
                    "当用户问「我以前做过 X 的经验吗」「我的 splatoon 笔记」时调本工具。"
    )
    public Map<String, Object> recall(
            @AiToolParam(name = "category", description = "分类名，可空", required = false)
            String category
    ) {
        return experienceStore.recall(MemoryToolContext.getUserId(), category);
    }
}
