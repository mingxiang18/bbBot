package com.bb.bot.common.util.aiChat.billing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiQuotaGrant;
import com.bb.bot.database.aiAgent.entity.AiUserQuota;
import com.bb.bot.database.aiAgent.service.IAiQuotaGrantService;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import com.bb.bot.database.aiAgent.service.IAiUserQuotaService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 月度限额判定：当月已花 vs 有效额度（默认/用户覆盖 + 本月授信）。
 *
 * <p>有效额度 = (ai_user_quota 覆盖 或 默认) + Σ(本月 ai_quota_grant.credit)。
 * 「重置」= 授一笔 credit = 当月已花，使剩余归满。所有人一视同仁（含 owner）。</p>
 *
 * @author ren
 */
@Slf4j
@Service
public class QuotaGuard {

    @Autowired
    private AiBillingProperties billingProperties;

    @Autowired
    private IAiTokenUsageService tokenUsageService;

    @Autowired
    private IAiUserQuotaService userQuotaService;

    @Autowired
    private IAiQuotaGrantService quotaGrantService;

    public static String currentMonth() {
        return YearMonth.now().toString(); // yyyy-MM
    }

    /** 是否启用硬阻断。 */
    public boolean enforceEnabled() {
        return billingProperties.isEnforce();
    }

    /** 当月用量状态：已花 / 有效额度 / 剩余。 */
    public QuotaStatus status(String userId, String platform) {
        String month = currentMonth();
        BigDecimal spent = nz(tokenUsageService.monthCostCny(userId, month));
        BigDecimal limit = effectiveLimitCny(userId, platform, month);
        return new QuotaStatus(month, spent, limit);
    }

    /** enforce 开启且当月已花 >= 有效额度 → true（阻断）。 */
    public boolean isOverLimit(String userId, String platform) {
        if (!billingProperties.isEnforce()) {
            return false;
        }
        QuotaStatus s = status(userId, platform);
        return s.getSpent().compareTo(s.getLimit()) >= 0;
    }

    /** 当月有效额度 = 基础额度（用户覆盖或默认） + 本月授信合计。 */
    public BigDecimal effectiveLimitCny(String userId, String platform, String month) {
        BigDecimal base = baseLimit(userId, platform);
        BigDecimal credit = monthCredit(userId, month);
        return base.add(credit);
    }

    /** 给该用户本月授一笔信（重置/提额机制）。 */
    public void grantCredit(String userId, BigDecimal creditCny, String grantedBy, String reason) {
        AiQuotaGrant g = new AiQuotaGrant();
        g.setUserId(userId);
        g.setMonth(currentMonth());
        g.setCreditCny(creditCny);
        g.setGrantedBy(grantedBy);
        g.setReason(reason);
        g.setCreatedAt(java.time.LocalDateTime.now());
        quotaGrantService.save(g);
    }

    private BigDecimal baseLimit(String userId, String platform) {
        AiUserQuota q = userQuotaService.getOne(new LambdaQueryWrapper<AiUserQuota>()
                .eq(AiUserQuota::getUserId, userId)
                .eq(AiUserQuota::getPlatform, StringUtils.defaultString(platform))
                .last("limit 1"));
        if (q == null) {
            // 退一步找平台无关（platform='')的覆盖
            q = userQuotaService.getOne(new LambdaQueryWrapper<AiUserQuota>()
                    .eq(AiUserQuota::getUserId, userId)
                    .eq(AiUserQuota::getPlatform, "")
                    .last("limit 1"));
        }
        if (q != null && q.getMonthlyLimitCny() != null) {
            return q.getMonthlyLimitCny();
        }
        return nz(billingProperties.getDefaultMonthlyLimitCny());
    }

    private BigDecimal monthCredit(String userId, String month) {
        List<AiQuotaGrant> grants = quotaGrantService.list(new LambdaQueryWrapper<AiQuotaGrant>()
                .eq(AiQuotaGrant::getUserId, userId)
                .eq(AiQuotaGrant::getMonth, month));
        BigDecimal sum = BigDecimal.ZERO;
        for (AiQuotaGrant g : grants) {
            sum = sum.add(nz(g.getCreditCny()));
        }
        return sum;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Getter
    public static class QuotaStatus {
        private final String month;
        private final BigDecimal spent;
        private final BigDecimal limit;

        public QuotaStatus(String month, BigDecimal spent, BigDecimal limit) {
            this.month = month;
            this.spent = spent;
            this.limit = limit;
        }

        public BigDecimal getRemaining() {
            BigDecimal r = limit.subtract(spent);
            return r.signum() < 0 ? BigDecimal.ZERO : r;
        }
    }
}
