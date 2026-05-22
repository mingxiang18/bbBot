package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每用户月度限额覆盖（CNY）。无记录则用配置默认 {@code aiAgent.billing.defaultMonthlyLimitCny}。
 */
@Data
@TableName("ai_user_quota")
public class AiUserQuota {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;
    private String platform;

    /** 月度限额（人民币元）。 */
    private BigDecimal monthlyLimitCny;

    private String updatedBy;
    private LocalDateTime updatedAt;
}
