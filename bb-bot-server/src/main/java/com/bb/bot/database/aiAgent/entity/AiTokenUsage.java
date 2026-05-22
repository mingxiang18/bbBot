package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每次模型调用的 token 用量（按 用户 / 模型 归属）。一次 HTTP 调用一行：
 * 主对话、廉价分类、内部总结、视觉桥接、每个工具循环步各自一行，靠 sessionId 串联。
 */
@Data
@TableName("ai_token_usage")
public class AiTokenUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String userId;
    private String platform;
    private String providerName;
    private String model;

    /** 模型层级：CHAT / LIGHT / VISION（{@code ModelTier}）。 */
    private String modelRole;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /** 命中缓存的输入 token 数（按 cache-hit 单价计费）；无则 0。 */
    private Integer cachedTokens;

    /** 本次调用的费用（人民币元，落库时按当时单价/汇率快照）。 */
    private BigDecimal costCny;

    /** 会话标识，串联同一轮里的多次调用。 */
    private String sessionId;

    private LocalDateTime createdAt;
}
