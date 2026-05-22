package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 按月授信记录（管理员重置/提额的机制 + 审计）。
 * 当月有效额度 = 用户限额(或默认) + Σ(本月 creditCny)。
 */
@Data
@TableName("ai_quota_grant")
public class AiQuotaGrant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;

    /** 归属月份，格式 yyyy-MM。 */
    private String month;

    /** 本次授信额度（人民币元）。 */
    private BigDecimal creditCny;

    private String grantedBy;
    private String reason;
    private LocalDateTime createdAt;
}
