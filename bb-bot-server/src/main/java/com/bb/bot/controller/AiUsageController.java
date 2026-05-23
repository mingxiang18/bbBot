package com.bb.bot.controller;

import com.bb.bot.common.util.aiChat.billing.AiBillingProperties;
import com.bb.bot.common.util.aiChat.billing.QuotaGuard;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import com.bb.bot.database.aiAgent.vo.UserModelUsage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量 / 费用查询 REST 接口。固定 token 鉴权（请求头 {@code X-Api-Token}），可查任意用户。
 *
 * <ul>
 *   <li>GET /api/ai/usage/{userId}?month=yyyy-MM —— 某用户某月：按模型 token+费用 + 额度/已花/剩余</li>
 *   <li>GET /api/ai/usage?month=yyyy-MM        —— 某月全部用户：按 用户+模型 聚合</li>
 * </ul>
 *
 * @author ren
 */
@RestController
@Api(tags = "AI 用量查询")
@RequestMapping("/api/ai/usage")
public class AiUsageController {

    @Autowired
    private IAiTokenUsageService tokenUsageService;

    @Autowired
    private QuotaGuard quotaGuard;

    @Autowired
    private AiBillingProperties billingProperties;

    @GetMapping("/{userId}")
    @ApiOperation("查询某用户某月用量/费用/额度")
    public Map<String, Object> userUsage(@RequestHeader(value = "X-Api-Token", required = false) String token,
                                         @PathVariable("userId") String userId,
                                         @RequestParam(value = "month", required = false) String month) {
        auth(token);
        String ym = normalizeMonth(month);
        BigDecimal spent = tokenUsageService.monthCostCny(userId, ym);
        BigDecimal limit = quotaGuard.effectiveLimitCny(userId, null, ym);
        List<UserModelUsage> byModel = tokenUsageService.monthCostByModel(userId, ym);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("month", ym);
        out.put("spentCny", spent);
        out.put("limitCny", limit);
        out.put("remainingCny", limit.subtract(spent).max(BigDecimal.ZERO));
        out.put("models", byModel);
        return out;
    }

    @GetMapping
    @ApiOperation("查询某月全部用户用量/费用（按 用户+模型）")
    public List<UserModelUsage> allUsage(@RequestHeader(value = "X-Api-Token", required = false) String token,
                                         @RequestParam(value = "month", required = false) String month) {
        auth(token);
        return tokenUsageService.monthCostAllUsers(normalizeMonth(month));
    }

    /** 固定 token 鉴权：未配置 token 视为关闭接口；不匹配返回 401。 */
    private void auth(String token) {
        String expected = billingProperties.getQueryApiToken();
        if (StringUtils.isBlank(expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "usage query api disabled (set aiAgent.billing.queryApiToken)");
        }
        if (!expected.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid X-Api-Token");
        }
    }

    /** month 缺省 = 当前月；格式 yyyy-MM，非法则 400。 */
    private String normalizeMonth(String month) {
        if (StringUtils.isBlank(month)) {
            return YearMonth.now().toString();
        }
        try {
            return YearMonth.parse(month.trim()).toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month 格式应为 yyyy-MM，例如 2026-05");
        }
    }
}
