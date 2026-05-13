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
public class RecordExperienceTool {

    @Autowired
    private ExperienceStore experienceStore;

    @AiTool(
            name = "record_experience",
            description = "记录一条分类经验笔记。category 是主题名（如 splatoon / nso / 工具偏好 / coding 等，" +
                    "仅允许字母/数字/中文/连字符），content 是要保存的文本。" +
                    "当你观察到值得长期记的信息（用户偏好、教训、配置、习惯）时调本工具。" +
                    "自动编号 + 去重（相同内容跳过）。"
    )
    public Map<String, Object> record(
            @AiToolParam(name = "category", description = "分类名")
            String category,
            @AiToolParam(name = "content", description = "要记录的文本")
            String content
    ) {
        return experienceStore.record(MemoryToolContext.getUserId(), category, content);
    }
}
