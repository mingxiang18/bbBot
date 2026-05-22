package com.bb.bot.database.aiAgent.vo;

import lombok.Data;

/**
 * 按 用户 + 模型 聚合后的 token 用量。供其他 service 查询展示用。
 */
@Data
public class UserModelUsage {

    private String userId;
    private String model;
    private Long sumPromptTokens;
    private Long sumCompletionTokens;
    private Long sumTotalTokens;
    private Long callCount;
}
