package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具对每个角色的允许 / 限流策略。
 *
 * <p>(tool_name, role) 唯一。没有匹配行时按 default 角色处理；default 也没有则默认拒绝。</p>
 */
@Data
@TableName("ai_tool_policy")
public class AiToolPolicy {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 工具名（与 @AiTool.name 对应） */
    private String toolName;

    /** 角色名 */
    private String role;

    /** 是否允许 */
    private Boolean allowed;

    /** 每小时调用上限。<=0 视为无限制 */
    private Integer rateLimitPerHour;

    private LocalDateTime updatedAt;
}
