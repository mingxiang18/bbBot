package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每次工具调用的审计日志（成功 / 拒绝 / 异常 / 超时都落）。
 */
@Data
@TableName("ai_tool_invocation_log")
public class AiToolInvocationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话标识（一次 agent 派活的串联 id） */
    private String sessionId;

    private String userId;
    private String platform;
    private String toolName;
    private String argsJson;
    private String resultJson;
    private Long latencyMs;

    /** ok / denied / error / timeout */
    private String status;

    private LocalDateTime createdAt;
}
