package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.skills.SkillManifest;
import com.bb.bot.aiAgent.skills.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SKILLS 体系的"按需展开"工具。
 *
 * <p>LLM 在 system prompt 里看到 SKILL 目录（name + description）后，
 * 决定"我要按 PDF-processing skill 干活"，调本工具传 name=pdf-processing → 拿到完整
 * SKILL.md body（含分步指南、示例、edge case），再按指南组合其他工具完成任务。</p>
 *
 * <p>这就是 agentskills.io 规范里的 progressive disclosure 第二级。</p>
 */
@Slf4j
@Component
public class LoadSkillTool {

    @Autowired
    private SkillRegistry registry;

    @AiTool(
            name = "load_skill",
            description = "加载某个已注册 SKILL 的完整指引（SKILL.md 全文）。" +
                    "当 system prompt 列出的某个 SKILL 名字看起来匹配用户任务时，调本工具按名字取详细步骤。" +
                    "返回 markdown 全文。"
    )
    public Map<String, Object> load(
            @AiToolParam(name = "name", description = "SKILL 名（小写 + 连字符）")
            String name
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        SkillManifest m = registry.get(name);
        if (m == null) {
            result.put("error", "skill_not_found");
            result.put("name", name);
            result.put("availableSkills", registry.names());
            return result;
        }
        try {
            String body = Files.readString(m.getSkillFile(), StandardCharsets.UTF_8);
            result.put("name", m.getName());
            result.put("description", m.getDescription());
            result.put("body", body);
            result.put("skillDir", m.getSkillDir().toString());
            return result;
        } catch (Exception e) {
            log.warn("加载 SKILL body 失败 name={}", name, e);
            result.put("error", "load_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
