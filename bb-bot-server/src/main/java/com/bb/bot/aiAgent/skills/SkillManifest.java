package com.bb.bot.aiAgent.skills;

import lombok.Getter;

/**
 * 一个 SKILL 加载后的元信息，来源于 {@code ai_skill} 表。
 *
 * <p>遵循 agentskills.io 规范：name 为 lowercase + hyphen；description 说明何时调用
 * （progressive disclosure 的 metadata 层，注入 system prompt）；body 为完整指引正文，
 * 由 {@link com.bb.bot.aiAgent.tools.LoadSkillTool} 按需取。</p>
 */
@Getter
public class SkillManifest {

    private final String name;
    private final String description;
    private final String body;

    public SkillManifest(String name, String description, String body) {
        this.name = name;
        this.description = description;
        this.body = body;
    }
}
