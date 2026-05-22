package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户额度审核申请。用户超额后 /额度申请 创建 pending，管理员审批后置 approved/rejected。
 */
@Data
@TableName("ai_quota_request")
public class AiQuotaRequest {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;
    private String platform;
    private String reason;

    /** pending / approved / rejected */
    private String status;

    private String decidedBy;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
