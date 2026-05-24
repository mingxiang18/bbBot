package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时 AI Agent 任务。owner 创建后由 {@code AiCronScheduler} 周期触发。
 */
@Data
@TableName("ai_cron_task")
public class AiCronTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建者 user id（owner 校验时用） */
    private String ownerUserId;

    /** 目标 IM 平台（BotType 字符串） */
    private String platform;

    /** 哪个 bot 的配置 —— 对应 BotConfig 里的 map key */
    private String botName;

    /** 目标群组 id（私聊则为空） */
    private String targetGroupId;

    /** 目标用户 id（群组则可选） */
    private String targetUserId;

    /** Spring CronExpression 格式（6 字段，秒 分 时 日 月 周；不支持年份字段） */
    private String cronExpr;

    /** 派给 LLM 的 prompt */
    private String prompt;

    /** 是否启用 */
    private Boolean enabled;

    /** 上次执行时间（用于跳过窗口期）*/
    private LocalDateTime lastRunAt;

    /** 上次执行状态：ok / error / skipped */
    private String lastStatus;

    private LocalDateTime createdAt;
}
