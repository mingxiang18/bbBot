package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结构化记忆卡片（记忆机制重构 Phase 2）。
 *
 * <p>取代"把对话总结成自由文本"的路线：每条长期记忆是一张有类型({@code type})、
 * 作用域({@code scope})、置信/重要度、老化字段的卡片。{@code summary} 是检索命脉
 * （对应 Claude Code frontmatter 的 description，selector 只看它决定选不选）。</p>
 *
 * <p>与旧 {@code ai_memory_fact} 并行：fact 表保留只读兼容、自然淘汰，不自动迁移。</p>
 */
@Data
@TableName("ai_memory_item")
public class AiMemoryItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 稳定外部 id（如 m_xxxx），用于索引引用 / supersede 链 */
    private String memoryKey;

    /** {@link com.bb.bot.aiAgent.memory.MemoryType} 的 code（小写） */
    private String type;

    /** {@link com.bb.bot.aiAgent.memory.MemoryScope} 的 code（小写） */
    private String scope;

    /** 所属用户（scope=user/user_in_group 非空） */
    private String userId;

    /** 所属群（scope=group/user_in_group 非空） */
    private String groupId;

    /** 被描述的用户（关系/梗类，可空） */
    private String subjectUserId;

    /** 一句话摘要，索引 + selector 判定的核心 */
    private String summary;

    /** 正文详情（可空） */
    private String body;

    /** 原因（preference/project_state 必填） */
    private String why;

    /** 使用方式（preference/project_state 必填） */
    private String howToApply;

    /** 来源事件 / 原话摘要，JSON 文本 */
    private String evidence;

    /** 标签 JSON array 字符串 */
    private String tags;

    /** 归一化 + n-gram 检索字段（ngram FULLTEXT） */
    private String searchText;

    /** {@link com.bb.bot.aiAgent.memory.MemoryStatus} 的 code（小写） */
    private String status;

    private BigDecimal confidence;

    private BigDecimal importance;

    /** 过期时间，可空（project_state 默认 created_at + 14 天） */
    private LocalDateTime expiresAt;

    /** 最近一次被再次确认 */
    private LocalDateTime lastSeenAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 来源 session */
    private String sourceSessionId;

    /** 替代本卡片的新卡片 memory_key（本卡 status=superseded 时填） */
    private String supersededBy;
}
