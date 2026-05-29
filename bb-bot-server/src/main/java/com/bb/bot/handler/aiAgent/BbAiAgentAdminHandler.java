package com.bb.bot.handler.aiAgent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.database.aiAgent.entity.AiToolInvocationLog;
import com.bb.bot.database.aiAgent.entity.AiUserRole;
import com.bb.bot.database.aiAgent.service.IAiToolInvocationLogService;
import com.bb.bot.database.aiAgent.service.IAiUserRoleService;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.constant.BotType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owner 专用：AI Agent 角色 / 审计管理命令。
 *
 * <ul>
 *   <li>{@code /aiAgent.role.grant <userId> <role>}</li>
 *   <li>{@code /aiAgent.role.revoke <userId> <role>}</li>
 *   <li>{@code /aiAgent.role.list <userId>}</li>
 *   <li>{@code /aiAgent.audit <userId> <days>}（default days = 7）</li>
 * </ul>
 *
 * <p>命令本身仅 owner 可调用，非 owner 触发也只会得到「无权限」回复。</p>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI Agent 管理")
public class BbAiAgentAdminHandler {

    @Autowired
    private BbReplies replies;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private IAiUserRoleService userRoleService;

    @Autowired
    private IAiToolInvocationLogService logService;

    private static final Pattern GRANT_RE = Pattern.compile("^/?aiAgent\\.role\\.grant\\s+(\\S+)\\s+(\\S+)\\s*$");
    private static final Pattern REVOKE_RE = Pattern.compile("^/?aiAgent\\.role\\.revoke\\s+(\\S+)\\s+(\\S+)\\s*$");
    private static final Pattern LIST_RE = Pattern.compile("^/?aiAgent\\.role\\.list\\s+(\\S+)\\s*$");
    private static final Pattern AUDIT_RE = Pattern.compile("^/?aiAgent\\.audit\\s+(\\S+)(?:\\s+(\\d+))?\\s*$");

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.role\\.grant\\s"}, name = "授予角色")
    public void grant(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = GRANT_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            replies.text(msg, "用法: /aiAgent.role.grant <userId> <role>");
            return;
        }
        String userId = m.group(1);
        String role = m.group(2);
        AiUserRole existing = userRoleService.getOne(new LambdaQueryWrapper<AiUserRole>()
                .eq(AiUserRole::getUserId, userId)
                .eq(AiUserRole::getPlatform, defaultIfBlank(msg.getBotType()))
                .eq(AiUserRole::getRole, role));
        if (existing != null) {
            replies.text(msg, "用户 " + userId + " 已具备 " + role + " 角色");
            return;
        }
        AiUserRole row = new AiUserRole();
        row.setUserId(userId);
        row.setPlatform(defaultIfBlank(msg.getBotType()));
        row.setRole(role);
        row.setGrantedBy(msg.getUserId());
        row.setGrantedAt(LocalDateTime.now());
        userRoleService.save(row);
        replies.text(msg, "已授予 " + userId + " 角色 " + role);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.role\\.revoke\\s"}, name = "撤销角色")
    public void revoke(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = REVOKE_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            replies.text(msg, "用法: /aiAgent.role.revoke <userId> <role>");
            return;
        }
        String userId = m.group(1);
        String role = m.group(2);
        boolean removed = userRoleService.remove(new LambdaQueryWrapper<AiUserRole>()
                .eq(AiUserRole::getUserId, userId)
                .eq(AiUserRole::getPlatform, defaultIfBlank(msg.getBotType()))
                .eq(AiUserRole::getRole, role));
        replies.text(msg, removed ? ("已撤销 " + userId + " 的 " + role) : "无此角色记录");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.role\\.list\\s"}, name = "查询角色")
    public void list(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = LIST_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            replies.text(msg, "用法: /aiAgent.role.list <userId>");
            return;
        }
        String userId = m.group(1);
        List<AiUserRole> rows = userRoleService.list(new LambdaQueryWrapper<AiUserRole>()
                .eq(AiUserRole::getUserId, userId));
        if (rows.isEmpty()) {
            replies.text(msg, userId + " 暂无角色（默认 user）");
            return;
        }
        StringBuilder sb = new StringBuilder(userId).append(" 的角色：\n");
        for (AiUserRole r : rows) {
            sb.append("- ").append(r.getRole()).append(" @ ")
                    .append(StringUtils.defaultIfBlank(r.getPlatform(), "*"))
                    .append(" by ").append(r.getGrantedBy()).append("\n");
        }
        replies.text(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.audit\\s"}, name = "工具调用审计")
    public void audit(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = AUDIT_RE.matcher(textOf(msg).trim());
        if (!m.find()) {
            replies.text(msg, "用法: /aiAgent.audit <userId> [days=7]");
            return;
        }
        String userId = m.group(1);
        int days = m.group(2) == null ? 7 : Integer.parseInt(m.group(2));
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<AiToolInvocationLog> rows = logService.list(new LambdaQueryWrapper<AiToolInvocationLog>()
                .eq(AiToolInvocationLog::getUserId, userId)
                .ge(AiToolInvocationLog::getCreatedAt, since)
                .orderByDesc(AiToolInvocationLog::getCreatedAt)
                .last("limit 30"));
        if (rows.isEmpty()) {
            replies.text(msg, userId + " 近 " + days + " 天无工具调用记录");
            return;
        }
        StringBuilder sb = new StringBuilder(userId).append(" 近 ").append(days).append(" 天工具调用（最多 30 条）：\n");
        for (AiToolInvocationLog r : rows) {
            sb.append("[").append(r.getStatus()).append("] ")
                    .append(r.getToolName()).append(" / ")
                    .append(r.getLatencyMs() == null ? "?" : r.getLatencyMs()).append("ms / ")
                    .append(r.getCreatedAt()).append("\n");
        }
        replies.text(msg, sb.toString().trim());
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        replies.text(msg, "无权限（仅 owner 可执行）");
        return true;
    }

    private String textOf(BbReceiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : msg.getMessageContentList()) {
            if (c.getData() != null) sb.append(c.getData().toString()).append(" ");
        }
        return sb.toString();
    }

    private String defaultIfBlank(String s) {
        return StringUtils.isBlank(s) ? "" : s;
    }
}
