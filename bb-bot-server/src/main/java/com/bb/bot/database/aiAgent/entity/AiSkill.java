package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DB 托管的 SKILL，是 SKILL 的唯一来源（增删改直接操作本表，reload 即时生效）。
 *
 * <p>对应 {@code SkillManifest}：{@link #description} 进 system prompt 做
 * progressive disclosure，{@link #body}（完整指引正文）由 load_skill 工具按需取。</p>
 */
@Data
@TableName("ai_skill")
public class AiSkill {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** SKILL 名：小写 + 连字符，唯一。 */
    private String name;

    /** 何时调用本 skill 的简短说明，注入 system prompt。 */
    private String description;

    /** SKILL 完整指引正文（等价于 SKILL.md 去掉 frontmatter 的部分，或全文均可）。 */
    private String body;

    /** 是否启用。 */
    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
