package com.bb.bot.common.util.aiChat.billing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 计费 / 限额配置。前缀 {@code aiAgent.billing}。
 *
 * <p>全部有缺省值：不配置时 {@code enforce=false} → 不阻断；无单价 → 费用记 0，行为与未启用计费一致。</p>
 *
 * @author ren
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-agent.billing")
public class AiBillingProperties {

    /** 结算币种（当前固定 CNY，保留可配）。 */
    private String settlementCurrency = "CNY";

    /** USD→CNY 汇率：USD 计价的模型按此换算成结算币种。 */
    private BigDecimal usdToCny = new BigDecimal("7.2");

    /** 默认月度限额（元）。用户无 ai_user_quota 覆盖时用它。 */
    private BigDecimal defaultMonthlyLimitCny = new BigDecimal("10.00");

    /** 是否启用月度限额硬阻断。关闭则只统计费用、不拦截。 */
    private boolean enforce = false;

    /**
     * 全局每日 token 上限兜底（所有用户合计）。&gt;0 时生效，独立于 per-user enforce：
     * 当天累计 token 达到上限后，暂停所有 AI 回复，防止异常流量/失控循环烧 token。0 = 关闭。
     */
    private long globalDailyTokenLimit = 0L;

    private Pricing pricing = new Pricing();

    @Data
    public static class Pricing {
        /** 是否启用定时拉取 LiteLLM 价格表刷新 USD 厂商单价。 */
        private boolean refreshEnabled = true;

        /** LiteLLM 社区维护的价格 JSON（USD，每 token）。文件在仓库根目录。 */
        private String litellmUrl =
                "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json";

        /** 刷新 cron（默认每天 04:30）。 */
        private String refreshCron = "0 30 4 * * *";
    }
}
