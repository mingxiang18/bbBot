package com.bb.bot.aiAgent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.core.AiToolDescriptor;
import com.bb.bot.aiAgent.core.AiToolRegistry;
import com.bb.bot.database.aiAgent.entity.AiToolPolicy;
import com.bb.bot.database.aiAgent.entity.AiUserRole;
import com.bb.bot.database.aiAgent.service.IAiToolPolicyService;
import com.bb.bot.database.aiAgent.service.IAiUserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限判定中心。
 *
 * <p>规则（先匹配先生效）：</p>
 * <ol>
 *   <li>用户 id 在 {@code aiAgent.owners} 配置里 → 视为 owner，所有工具放行</li>
 *   <li>查 {@code ai_user_role} 拿用户在该平台的角色集合（无记录 → {"user"}）</li>
 *   <li>对每个角色查 {@code ai_tool_policy.allowed}，命中任一 allowed=true 即放行</li>
 *   <li>都不命中 → 默认拒绝</li>
 * </ol>
 *
 * <p>额外：MVP 阶段不实现 rate_limit_per_hour 的强制执行，只在审计日志中保留字段，
 * 后续 v1.1 加 Caffeine 计数器。</p>
 */
@Slf4j
@Component
public class AiAgentAuthService {

    @Autowired
    private IAiUserRoleService userRoleService;

    @Autowired
    private IAiToolPolicyService policyService;

    @Autowired
    private AiToolRegistry registry;

    /** application.yml 里逗号分隔的 owner 用户 id 列表。 */
    @Value("${aiAgent.owners:1105048721}")
    private String ownersCsv;

    public boolean isOwner(String userId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(ownersCsv)) {
            return false;
        }
        for (String o : ownersCsv.split(",")) {
            if (o.trim().equals(userId)) return true;
        }
        return false;
    }

    /**
     * @return 检查结果，包含是否放行 + 命中的角色 + 拒绝原因（如果拒绝）
     */
    public AuthDecision canInvoke(String userId, String platform, String toolName) {
        AiToolDescriptor desc = registry.get(toolName);
        if (desc == null) {
            return AuthDecision.deny("unknown_tool", "user");
        }
        if (isOwner(userId)) {
            return AuthDecision.allow("owner");
        }
        if (desc.isRequiresOwner()) {
            return AuthDecision.deny("requires_owner", "user");
        }

        Set<String> roles = rolesOf(userId, platform);
        // 始终包含默认角色，方便 policy 表用 default 兜底
        roles.add("user");

        for (String role : roles) {
            AiToolPolicy hit = policyService.getOne(new LambdaQueryWrapper<AiToolPolicy>()
                    .eq(AiToolPolicy::getToolName, toolName)
                    .eq(AiToolPolicy::getRole, role)
                    .last("limit 1"));
            if (hit != null && Boolean.TRUE.equals(hit.getAllowed())) {
                return AuthDecision.allow(role);
            }
        }
        return AuthDecision.deny("no_matching_policy", String.join(",", roles));
    }

    public Set<String> rolesOf(String userId, String platform) {
        if (StringUtils.isBlank(userId)) return new LinkedHashSet<>();
        List<AiUserRole> rows = userRoleService.list(new LambdaQueryWrapper<AiUserRole>()
                .eq(AiUserRole::getUserId, userId)
                .eq(StringUtils.isNoneBlank(platform), AiUserRole::getPlatform, platform));
        Set<String> roles = new HashSet<>();
        for (AiUserRole r : rows) {
            if (StringUtils.isNoneBlank(r.getRole())) roles.add(r.getRole());
        }
        return roles;
    }

    public static class AuthDecision {
        public final boolean allowed;
        public final String reason;
        public final String matchedRole;

        private AuthDecision(boolean allowed, String reason, String matchedRole) {
            this.allowed = allowed;
            this.reason = reason;
            this.matchedRole = matchedRole;
        }

        public static AuthDecision allow(String role) {
            return new AuthDecision(true, "allowed", role);
        }

        public static AuthDecision deny(String reason, String role) {
            return new AuthDecision(false, reason, role);
        }
    }
}
