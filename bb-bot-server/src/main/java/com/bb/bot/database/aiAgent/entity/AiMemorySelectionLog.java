package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆选择审计（Phase 3，可观测 "为什么机器人用了这条记忆"）。
 * 带 TTL，由 {@code MemoryLifecycleSweeper} 定时清理，防膨胀。
 */
@Data
@TableName("ai_memory_selection_log")
public class AiMemorySelectionLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;
    private String groupId;

    /** 当前消息摘要 */
    private String queryText;

    /** 候选 memory_key 列表（逗号分隔） */
    private String candidateKeys;

    /** 选中的 memory_key 列表（逗号分隔） */
    private String selectedKeys;

    /** 使用的选择器模型档位 */
    private String selectorModel;

    private LocalDateTime createdAt;
}
