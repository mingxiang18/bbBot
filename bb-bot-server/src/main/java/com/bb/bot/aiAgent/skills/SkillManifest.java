package com.bb.bot.aiAgent.skills;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

/**
 * 一个 SKILL.md 加载后的元信息（不含 body —— body 由 LoadSkillTool 按需读）。
 * <p>遵循 agentskills.io 规范：</p>
 * <ul>
 *   <li>{@code name}：lowercase + hyphen，必须与父目录名一致</li>
 *   <li>{@code description}：何时调用本 skill 的说明（≤1024 字符）</li>
 *   <li>{@code skillDir}：SKILL.md 所在目录，便于解析 scripts/ references/ assets/</li>
 *   <li>{@code skillFile}：SKILL.md 本身</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class SkillManifest {
    private final String name;
    private final String description;
    private final Path skillDir;
    private final Path skillFile;
}
