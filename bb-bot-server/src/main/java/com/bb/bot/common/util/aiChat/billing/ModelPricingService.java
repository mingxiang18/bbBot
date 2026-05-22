package com.bb.bot.common.util.aiChat.billing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiModelPricing;
import com.bb.bot.database.aiAgent.service.IAiModelPricingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 模型计费：按 (provider, model) 查单价并把一次调用的 token 折算成 CNY 费用。
 *
 * <p>单价存在 {@code ai_model_pricing}（按百万 token，可能 CNY 或 USD）。USD 行按
 * {@link AiBillingProperties#getUsdToCny()} 换算。命中缓存的输入 token 走 cache-hit 单价。</p>
 *
 * @author ren
 */
@Slf4j
@Service
public class ModelPricingService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    @Autowired
    private IAiModelPricingService pricingService;

    @Autowired
    private AiBillingProperties billingProperties;

    /** (provider|model) → 单价行。命中价格变更时 invalidate。 */
    private final Cache<String, Optional<AiModelPricing>> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 计算一次调用的费用（CNY），按四类 token 桶分别计价：
     * 输入(cache-miss)、输入(cache-hit/read)、写缓存(cache-write，仅 Anthropic)、输出（含 reasoning）。
     * 无单价则返回 0（记 warn，靠管理员补价）。
     *
     * @param promptTokens     总输入 token（含 cache-hit；不含 Anthropic 的 cache-write）
     * @param cachedTokens     命中缓存的输入 token（cache read）
     * @param cacheWriteTokens 写缓存的输入 token（cache creation）
     * @param completionTokens 输出 token（含 reasoning）
     */
    public BigDecimal costCny(String provider, String model,
                              int promptTokens, int cachedTokens, int cacheWriteTokens, int completionTokens) {
        AiModelPricing p = find(provider, model);
        if (p == null) {
            log.warn("缺少模型单价，费用按 0 计：provider={} model={}（用 /价格设置 补价）", provider, model);
            return BigDecimal.ZERO;
        }
        BigDecimal inputPerM = nz(p.getInputPerMillion());
        BigDecimal outputPerM = nz(p.getOutputPerMillion());
        BigDecimal cacheHitPerM = p.getCacheHitInputPerMillion() != null ? p.getCacheHitInputPerMillion() : inputPerM;
        // 写缓存价缺省按标准输入价（多数厂商写缓存免费 → 通常为 0；只有 Anthropic 配了才有值）
        BigDecimal cacheWritePerM = p.getCacheWriteInputPerMillion() != null ? p.getCacheWriteInputPerMillion() : inputPerM;

        int cached = Math.max(0, Math.min(cachedTokens, promptTokens));
        int nonCached = Math.max(0, promptTokens - cached);
        int cacheWrite = Math.max(0, cacheWriteTokens);

        BigDecimal costNative = cacheHitPerM.multiply(BigDecimal.valueOf(cached))
                .add(inputPerM.multiply(BigDecimal.valueOf(nonCached)))
                .add(cacheWritePerM.multiply(BigDecimal.valueOf(cacheWrite)))
                .add(outputPerM.multiply(BigDecimal.valueOf(completionTokens)))
                .divide(MILLION, 8, RoundingMode.HALF_UP);

        BigDecimal costCny = "USD".equalsIgnoreCase(p.getCurrency())
                ? costNative.multiply(nz(billingProperties.getUsdToCny()))
                : costNative;
        return costCny.setScale(6, RoundingMode.HALF_UP);
    }

    /** 查单价：(provider,model) 精确 → 仅 model → null。结果（含 null）进缓存。 */
    public AiModelPricing find(String provider, String model) {
        if (StringUtils.isBlank(model)) {
            return null;
        }
        String key = StringUtils.defaultString(provider) + "|" + model;
        return cache.get(key, k -> Optional.ofNullable(lookup(provider, model))).orElse(null);
    }

    private AiModelPricing lookup(String provider, String model) {
        AiModelPricing exact = pricingService.getOne(new LambdaQueryWrapper<AiModelPricing>()
                .eq(StringUtils.isNoneBlank(provider), AiModelPricing::getProviderName, provider)
                .eq(AiModelPricing::getModel, model)
                .last("limit 1"));
        if (exact != null) {
            return exact;
        }
        List<AiModelPricing> byModel = pricingService.list(new LambdaQueryWrapper<AiModelPricing>()
                .eq(AiModelPricing::getModel, model)
                .last("limit 1"));
        return byModel.isEmpty() ? null : byModel.get(0);
    }

    /** 单价被改动后调用，清空缓存。 */
    public void invalidateCache() {
        cache.invalidateAll();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
