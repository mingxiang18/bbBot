package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期事实（对应 openhanako 的 FactStore）。
 *
 * <p>关键点：</p>
 * <ul>
 *   <li>{@code search_text} 是归一化 + n-gram 后用于 MySQL FULLTEXT 的列</li>
 *   <li>{@code tags} 存 JSON array 字符串，搜索用 LIKE / 应用层 parse</li>
 *   <li>v2 没有 importance / decay 字段，靠时间窗 + tag 命中数排序</li>
 * </ul>
 */
@Data
@TableName("ai_memory_fact")
public class AiMemoryFact {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 事实原文 */
    private String fact;

    /** 归一化 + n-gram 后的全文检索字段 */
    private String searchText;

    /** JSON array string，例如 ["splatoon","武器"]  */
    private String tags;

    /** 事实发生时间（可空，未必每条都有） */
    private LocalDateTime factTime;

    /** 从哪个 session 蒸馏出来 */
    private String sourceSessionId;

    private LocalDateTime createdAt;
}
