package com.bb.bot.database.aiAgent.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按 用户 + 模型 聚合后的 token 用量 + 费用。供其他 service 查询展示用。
 */
@Data
public class UserModelUsage {

    private String userId;
    private String model;
    private Long sumPromptTokens;
    private Long sumCompletionTokens;
    private Long sumTotalTokens;
    private Long callCount;
    /** 该 用户+模型 的费用合计（人民币元）。 */
    private BigDecimal sumCostCny;
}
