package com.bb.bot.common.util.aiChat.billing;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.aiChat.provider.AIProviderProperties;
import com.bb.bot.database.aiAgent.entity.AiModelPricing;
import com.bb.bot.database.aiAgent.entity.AiTokenUsage;
import com.bb.bot.database.aiAgent.service.IAiModelPricingService;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时从 LiteLLM 社区价格表刷新模型单价（USD，每 token）。只刷新「我们实际配置/用过」的
 * (provider, model)，避免把上千个模型灌进表；source=seed/manual 的行不覆盖（保护 CNY 官方价 / 人工价）。
 *
 * <p>厂商 API 都不返回费用，社区维护的 LiteLLM JSON 是最省维护的「价格接口」。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class ModelPricingRefreshJob {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    @Autowired
    private AiBillingProperties billingProperties;

    @Autowired
    private AIProviderProperties providerProperties;

    @Autowired
    private IAiModelPricingService pricingService;

    @Autowired
    private IAiTokenUsageService tokenUsageService;

    @Autowired
    private ModelPricingService modelPricingService;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** 启动后跑一次（延迟，等表建好）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            refreshNow();
        } catch (Exception e) {
            log.warn("启动期价格刷新失败（忽略）", e);
        }
    }

    @Scheduled(cron = "${aiAgent.billing.pricing.refreshCron:0 30 4 * * *}")
    public void scheduled() {
        try {
            refreshNow();
        } catch (Exception e) {
            log.warn("定时价格刷新失败（忽略）", e);
        }
    }

    /**
     * 立即刷新。返回人类可读结果（供 /价格刷新 命令回显）。
     */
    public String refreshNow() {
        if (!billingProperties.getPricing().isRefreshEnabled()) {
            return "价格刷新已关闭（aiAgent.billing.pricing.refreshEnabled=false）";
        }
        String url = billingProperties.getPricing().getLitellmUrl();
        JSONObject root;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return "拉取 LiteLLM 价格表失败：HTTP " + resp.statusCode();
            }
            root = JSON.parseObject(resp.body());
        } catch (Exception e) {
            log.warn("拉取 LiteLLM 价格表失败 url={}", url, e);
            return "拉取 LiteLLM 价格表失败：" + e.getMessage() + "（用旧价兜底）";
        }
        if (root == null) {
            return "LiteLLM 价格表解析为空";
        }

        int updated = 0;
        int skipped = 0;
        for (String[] pair : candidatePairs()) {
            String provider = pair[0];
            String model = pair[1];
            JSONObject entry = root.getJSONObject(model);
            if (entry == null || entry.get("input_cost_per_token") == null) {
                continue;
            }
            // 已有 seed/manual 行 → 保护，不覆盖
            AiModelPricing existing = pricingService.getOne(new LambdaQueryWrapper<AiModelPricing>()
                    .eq(AiModelPricing::getProviderName, provider)
                    .eq(AiModelPricing::getModel, model)
                    .last("limit 1"));
            if (existing != null && ("seed".equals(existing.getSource()) || "manual".equals(existing.getSource()))) {
                skipped++;
                continue;
            }
            BigDecimal inPerM = perMillion(entry.getBigDecimal("input_cost_per_token"));
            BigDecimal outPerM = perMillion(entry.getBigDecimal("output_cost_per_token"));
            // cache 读单价：anthropic/openai 用 cache_read_input_token_cost；部分条目用 input_cost_per_token_cache(_hit)
            BigDecimal cacheReadPerM = perMillionOrNull(entry,
                    "cache_read_input_token_cost", "input_cost_per_token_cache_hit", "input_cost_per_token_cache");
            // cache 写单价：anthropic cache_creation_input_token_cost
            BigDecimal cacheWritePerM = perMillionOrNull(entry, "cache_creation_input_token_cost");
            AiModelPricing row = existing != null ? existing : new AiModelPricing();
            row.setProviderName(provider);
            row.setModel(model);
            row.setCurrency("USD");
            row.setInputPerMillion(inPerM);
            row.setOutputPerMillion(outPerM);
            row.setCacheHitInputPerMillion(cacheReadPerM);
            row.setCacheWriteInputPerMillion(cacheWritePerM);
            row.setSource("litellm");
            row.setUpdatedAt(LocalDateTime.now());
            pricingService.saveOrUpdate(row);
            updated++;
        }
        modelPricingService.invalidateCache();
        String msg = String.format("价格刷新完成：更新 %d 个、跳过(seed/manual保护) %d 个", updated, skipped);
        log.info(msg);
        return msg;
    }

    /** 候选 (kind, model)：当前配置的命名模型 + token 用量里出现过的模型。 */
    private Set<String[]> candidatePairs() {
        Set<String> seen = new LinkedHashSet<>();
        Set<String[]> pairs = new LinkedHashSet<>();
        for (com.bb.bot.common.util.aiChat.provider.ModelSpec m : providerProperties.getModels().values()) {
            if (m != null) {
                addPair(pairs, seen, m.getKind(), m.getModel());
            }
        }
        // token 用量里实际出现过的 (provider, model)
        try {
            List<Map<String, Object>> rows = tokenUsageService.listMaps(
                    new QueryWrapper<AiTokenUsage>().select("DISTINCT provider_name, model"));
            for (Map<String, Object> r : rows) {
                Object p = r.get("provider_name");
                Object m = r.get("model");
                addPair(pairs, seen, p == null ? null : p.toString(), m == null ? null : m.toString());
            }
        } catch (Exception e) {
            log.debug("读取 token 用量里的模型集合失败（忽略）", e);
        }
        return pairs;
    }

    private void addPair(Set<String[]> pairs, Set<String> seen, String provider, String model) {
        if (StringUtils.isBlank(model)) {
            return;
        }
        String prov = StringUtils.defaultString(provider, "openai");
        String key = prov + "|" + model;
        if (seen.add(key)) {
            pairs.add(new String[]{prov, model});
        }
    }

    private BigDecimal perMillion(BigDecimal perToken) {
        if (perToken == null) {
            return BigDecimal.ZERO;
        }
        return perToken.multiply(MILLION).setScale(4, RoundingMode.HALF_UP);
    }

    /** 取 entry 里第一个存在的 key（每 token 价）换算成每百万；都不存在返回 null（保持列为空，计算时回退）。 */
    private BigDecimal perMillionOrNull(JSONObject entry, String... keys) {
        for (String k : keys) {
            if (entry.get(k) != null) {
                return entry.getBigDecimal(k).multiply(MILLION).setScale(4, RoundingMode.HALF_UP);
            }
        }
        return null;
    }
}
