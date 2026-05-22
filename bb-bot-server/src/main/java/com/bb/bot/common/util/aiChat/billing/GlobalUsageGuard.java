package com.bb.bot.common.util.aiChat.billing;

import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局每日 token 兜底：所有用户合计当天 token 达上限后暂停 AI 回复，防止异常流量/失控循环烧 token。
 *
 * <p>用内存计数（{@link AtomicLong}）+ 每天首次访问从 DB 回填（重启不清零、跨天自动重置），
 * 避免每次请求都 SUM 全表。独立于 per-user {@code enforce}，只要 {@code globalDailyTokenLimit>0} 生效。</p>
 *
 * @author ren
 */
@Slf4j
@Service
public class GlobalUsageGuard {

    @Autowired
    private AiBillingProperties billingProperties;

    @Autowired
    private IAiTokenUsageService tokenUsageService;

    private volatile LocalDate trackedDay;
    private final AtomicLong tokensToday = new AtomicLong();

    /** 跨天则从 DB 回填当天已用 token（重启后也能续上）。 */
    private synchronized void ensureDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(trackedDay)) {
            long sum = 0L;
            try {
                sum = tokenUsageService.sumTotalTokensSince(today.atStartOfDay());
            } catch (Exception e) {
                log.warn("回填当天全局 token 计数失败，从 0 起算", e);
            }
            tokensToday.set(sum);
            trackedDay = today;
        }
    }

    /** 累加（落库后调用）。 */
    public void add(long tokens) {
        if (tokens <= 0) {
            return;
        }
        ensureDay();
        tokensToday.addAndGet(tokens);
    }

    /** 当天是否已达全局上限。limit<=0 表示关闭。 */
    public boolean isOverDailyLimit() {
        long limit = billingProperties.getGlobalDailyTokenLimit();
        if (limit <= 0) {
            return false;
        }
        ensureDay();
        return tokensToday.get() >= limit;
    }

    public long tokensToday() {
        ensureDay();
        return tokensToday.get();
    }

    public long dailyLimit() {
        return billingProperties.getGlobalDailyTokenLimit();
    }
}
