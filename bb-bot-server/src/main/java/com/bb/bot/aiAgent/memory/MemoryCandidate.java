package com.bb.bot.aiAgent.memory;

import lombok.Data;

import java.util.List;

/**
 * LLM 从 session 抽出的候选记忆卡片（落库前的中间态）。
 * 字段名与 {@link MemoryExtractor} 的 prompt 约定一致，fastjson2 直接反序列化。
 */
@Data
public class MemoryCandidate {

    private String type;
    private String scope;
    private String summary;
    private String body;
    private String why;
    private String howToApply;

    /** 被描述的用户（关系/梗类用，可空） */
    private String subjectUserId;

    private Double confidence;
    private Double importance;

    /** 相对过期天数（临时事件/项目状态用，可空；最终转绝对 expires_at） */
    private Integer expiresInDays;

    /** 若本卡覆盖某条已有记忆，给其 memory_key */
    private String supersedesKey;

    private List<String> evidence;
    private List<String> tags;
}
