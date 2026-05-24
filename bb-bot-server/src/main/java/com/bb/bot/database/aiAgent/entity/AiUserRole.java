package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户在 AI Agent 体系内的角色绑定。
 *
 * <p>同一 (user_id, platform, role) 组合唯一。常见角色：</p>
 * <ul>
 *   <li>owner —— 完全权限，应用启动时由 aiAgent.owners 配置预置</li>
 *   <li>admin —— 可调用敏感工具（shell / file write 等）</li>
 *   <li>user  —— 只读工具 / 无副作用工具（默认）</li>
 * </ul>
 */
@Data
@TableName("ai_user_role")
public class AiUserRole {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 平台用户 id（QQ uid / TG chat id / Discord user id / BB userId 等） */
    private String userId;

    /** 平台标识：BotType 常量字符串，例如 ONEBOT / TELEGRAM / DISCORD */
    private String platform;

    /** 角色名 */
    private String role;

    /** 授权操作发起者的 userId（owner 角色为 system） */
    private String grantedBy;

    private LocalDateTime grantedAt;
}
