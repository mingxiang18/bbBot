package com.bb.bot.handler.aiAgent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.aiChat.billing.ModelPricingRefreshJob;
import com.bb.bot.common.util.aiChat.billing.ModelPricingService;
import com.bb.bot.common.util.aiChat.billing.QuotaGuard;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiModelPricing;
import com.bb.bot.database.aiAgent.entity.AiQuotaRequest;
import com.bb.bot.database.aiAgent.entity.AiUserQuota;
import com.bb.bot.database.aiAgent.service.IAiModelPricingService;
import com.bb.bot.database.aiAgent.service.IAiQuotaRequestService;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import com.bb.bot.database.aiAgent.service.IAiUserQuotaService;
import com.bb.bot.database.aiAgent.vo.UserModelUsage;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 计费 / 额度命令。
 *
 * <p>用户：{@code /我的用量}、{@code /额度申请 [理由]}。</p>
 * <p>管理员（owner）：{@code /用量 <userId>}、{@code /额度设置 <userId> <月限额>}、
 * {@code /额度重置 <userId>}、{@code /额度审批 <userId> [新限额]}、{@code /待审批}、
 * {@code /价格刷新}、{@code /价格设置 <provider> <model> <input/百万> <output/百万> [币种] [缓存命中/百万]}、{@code /价格}。</p>
 *
 * <p>命令是关键字规则：匹配后 dispatcher 不再走默认 AI 回复，故超额用户仍能用申请/查询命令。</p>
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI 计费管理")
public class BbAiBillingHandler {

    @Autowired
    private BbReplies bbReplies;
    @Autowired
    private AiAgentAuthService authService;
    @Autowired
    private QuotaGuard quotaGuard;
    @Autowired
    private IAiTokenUsageService tokenUsageService;
    @Autowired
    private IAiUserQuotaService userQuotaService;
    @Autowired
    private IAiQuotaRequestService quotaRequestService;
    @Autowired
    private IAiModelPricingService pricingService;
    @Autowired
    private ModelPricingService modelPricingService;
    @Autowired
    private ModelPricingRefreshJob pricingRefreshJob;

    private static final Pattern P_APPLY = Pattern.compile("^/?额度申请\\s*([\\s\\S]*)$");
    private static final Pattern P_USAGE = Pattern.compile("^/?用量\\s+(\\S+)\\s*$");
    private static final Pattern P_SET = Pattern.compile("^/?额度设置\\s+(\\S+)\\s+([0-9.]+)\\s*$");
    private static final Pattern P_RESET = Pattern.compile("^/?额度重置\\s+(\\S+)\\s*$");
    private static final Pattern P_APPROVE = Pattern.compile("^/?额度审批\\s+(\\S+)(?:\\s+([0-9.]+))?\\s*$");
    private static final Pattern P_PRICE_SET = Pattern.compile(
            "^/?价格设置\\s+(\\S+)\\s+(\\S+)\\s+([0-9.]+)\\s+([0-9.]+)(?:\\s+([A-Za-z]{3}))?(?:\\s+([0-9.]+))?(?:\\s+([0-9.]+))?\\s*$");

    // ============ 用户命令 ============

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?我的用量"}, name = "我的用量")
    public void myUsage(BbReceiveMessage msg) {
        reply(msg, renderUsage(msg.getUserId(), msg.getBotType(), "你本月"));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?额度申请"}, name = "额度申请")
    public void applyQuota(BbReceiveMessage msg) {
        Matcher m = P_APPLY.matcher(textOf(msg).trim());
        String reason = m.find() ? m.group(1).trim() : "";
        AiQuotaRequest req = new AiQuotaRequest();
        req.setUserId(msg.getUserId());
        req.setPlatform(StringUtils.defaultString(msg.getBotType()));
        req.setReason(reason);
        req.setStatus("pending");
        req.setCreatedAt(LocalDateTime.now());
        quotaRequestService.save(req);
        reply(msg, "已提交额度申请（#" + req.getId() + "），请等待管理员审核。\n"
                + "管理员可用『/待审批』查看、『/额度审批 " + msg.getUserId() + "』通过。");
    }

    // ============ 管理员命令 ============

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?用量\\s"}, name = "查询用量")
    public void queryUsage(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = P_USAGE.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /用量 <userId>");
            return;
        }
        String userId = m.group(1);
        reply(msg, renderUsage(userId, null, userId + " 本月"));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?额度设置\\s"}, name = "设置额度")
    public void setQuota(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = P_SET.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /额度设置 <userId> <月限额CNY>");
            return;
        }
        String userId = m.group(1);
        BigDecimal limit = new BigDecimal(m.group(2));
        AiUserQuota q = userQuotaService.getOne(new LambdaQueryWrapper<AiUserQuota>()
                .eq(AiUserQuota::getUserId, userId).eq(AiUserQuota::getPlatform, ""));
        if (q == null) {
            q = new AiUserQuota();
            q.setUserId(userId);
            q.setPlatform("");
        }
        q.setMonthlyLimitCny(limit);
        q.setUpdatedBy(msg.getUserId());
        q.setUpdatedAt(LocalDateTime.now());
        userQuotaService.saveOrUpdate(q);
        reply(msg, "已设置 " + userId + " 月度额度为 ¥" + limit.toPlainString());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?额度重置\\s"}, name = "重置额度")
    public void resetQuota(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = P_RESET.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /额度重置 <userId>");
            return;
        }
        String userId = m.group(1);
        BigDecimal spent = tokenUsageService.monthCostCny(userId, QuotaGuard.currentMonth());
        quotaGuard.grantCredit(userId, spent, msg.getUserId(), "reset");
        markRequestsApproved(userId, msg.getUserId());
        reply(msg, "已重置 " + userId + " 本月额度（授信 ¥" + spent.toPlainString() + "，剩余归满）。");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?额度审批\\s"}, name = "审批额度")
    public void approveQuota(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = P_APPROVE.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /额度审批 <userId> [新限额CNY]");
            return;
        }
        String userId = m.group(1);
        String newLimit = m.group(2);
        if (newLimit != null) {
            AiUserQuota q = userQuotaService.getOne(new LambdaQueryWrapper<AiUserQuota>()
                    .eq(AiUserQuota::getUserId, userId).eq(AiUserQuota::getPlatform, ""));
            if (q == null) {
                q = new AiUserQuota();
                q.setUserId(userId);
                q.setPlatform("");
            }
            q.setMonthlyLimitCny(new BigDecimal(newLimit));
            q.setUpdatedBy(msg.getUserId());
            q.setUpdatedAt(LocalDateTime.now());
            userQuotaService.saveOrUpdate(q);
        } else {
            BigDecimal spent = tokenUsageService.monthCostCny(userId, QuotaGuard.currentMonth());
            quotaGuard.grantCredit(userId, spent, msg.getUserId(), "approve-reset");
        }
        int n = markRequestsApproved(userId, msg.getUserId());
        reply(msg, "已审批通过 " + userId + (newLimit != null ? "（新限额 ¥" + newLimit + "）" : "（本月重置）")
                + "，处理 " + n + " 条申请。");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?待审批"}, name = "待审批列表")
    public void pendingRequests(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        List<AiQuotaRequest> rows = quotaRequestService.list(new LambdaQueryWrapper<AiQuotaRequest>()
                .eq(AiQuotaRequest::getStatus, "pending")
                .orderByAsc(AiQuotaRequest::getCreatedAt).last("limit 30"));
        if (rows.isEmpty()) {
            reply(msg, "当前无待审批的额度申请。");
            return;
        }
        StringBuilder sb = new StringBuilder("待审批额度申请（最多 30 条）：\n");
        for (AiQuotaRequest r : rows) {
            sb.append("#").append(r.getId()).append(" ").append(r.getUserId())
                    .append(" 理由：").append(StringUtils.defaultIfBlank(r.getReason(), "(无)"))
                    .append("  ").append(r.getCreatedAt()).append("\n");
        }
        sb.append("通过：/额度审批 <userId> [新限额]");
        reply(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?价格刷新"}, name = "价格刷新")
    public void refreshPricing(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        reply(msg, pricingRefreshJob.refreshNow());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?价格设置\\s"}, name = "设置价格")
    public void setPricing(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = P_PRICE_SET.matcher(textOf(msg).trim());
        if (!m.find()) {
            reply(msg, "用法: /价格设置 <provider> <model> <输入价/百万> <输出价/百万> [币种=CNY] [缓存命中价/百万] [写缓存价/百万]");
            return;
        }
        String provider = m.group(1);
        String model = m.group(2);
        AiModelPricing row = pricingService.getOne(new LambdaQueryWrapper<AiModelPricing>()
                .eq(AiModelPricing::getProviderName, provider).eq(AiModelPricing::getModel, model));
        if (row == null) {
            row = new AiModelPricing();
            row.setProviderName(provider);
            row.setModel(model);
        }
        row.setInputPerMillion(new BigDecimal(m.group(3)));
        row.setOutputPerMillion(new BigDecimal(m.group(4)));
        row.setCurrency(StringUtils.defaultIfBlank(m.group(5), "CNY").toUpperCase());
        row.setCacheHitInputPerMillion(m.group(6) != null ? new BigDecimal(m.group(6)) : null);
        row.setCacheWriteInputPerMillion(m.group(7) != null ? new BigDecimal(m.group(7)) : null);
        row.setSource("manual");
        row.setUpdatedAt(LocalDateTime.now());
        pricingService.saveOrUpdate(row);
        modelPricingService.invalidateCache();
        reply(msg, String.format("已设置 %s/%s 单价：输入 %s、输出 %s、币种 %s%s%s（/百万 token）",
                provider, model, row.getInputPerMillion().toPlainString(), row.getOutputPerMillion().toPlainString(),
                row.getCurrency(),
                row.getCacheHitInputPerMillion() != null ? "、缓存命中 " + row.getCacheHitInputPerMillion().toPlainString() : "",
                row.getCacheWriteInputPerMillion() != null ? "、写缓存 " + row.getCacheWriteInputPerMillion().toPlainString() : ""));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?价格(查询)?$"}, name = "价格列表")
    public void listPricing(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        List<AiModelPricing> rows = pricingService.list(new LambdaQueryWrapper<AiModelPricing>()
                .orderByAsc(AiModelPricing::getProviderName).last("limit 60"));
        if (rows.isEmpty()) {
            reply(msg, "暂无价格记录。用 /价格设置 添加。");
            return;
        }
        StringBuilder sb = new StringBuilder("模型单价（/百万 token）：\n");
        for (AiModelPricing r : rows) {
            sb.append(r.getProviderName()).append("/").append(r.getModel())
                    .append(" 入").append(r.getInputPerMillion().toPlainString())
                    .append(" 出").append(r.getOutputPerMillion().toPlainString());
            if (r.getCacheHitInputPerMillion() != null) {
                sb.append(" 命中").append(r.getCacheHitInputPerMillion().toPlainString());
            }
            if (r.getCacheWriteInputPerMillion() != null) {
                sb.append(" 写缓存").append(r.getCacheWriteInputPerMillion().toPlainString());
            }
            sb.append(" ").append(r.getCurrency()).append(" [").append(r.getSource()).append("]\n");
        }
        reply(msg, sb.toString().trim());
    }

    // ============ helpers ============

    private String renderUsage(String userId, String platform, String who) {
        QuotaGuard.QuotaStatus st = quotaGuard.status(userId, platform);
        List<UserModelUsage> byModel = tokenUsageService.monthCostByModel(userId, st.getMonth());
        StringBuilder sb = new StringBuilder();
        sb.append(who).append("（").append(st.getMonth()).append("）AI 用量：\n");
        long totalTokens = 0;
        for (UserModelUsage u : byModel) {
            totalTokens += u.getSumTotalTokens() == null ? 0 : u.getSumTotalTokens();
            sb.append("· ").append(u.getModel())
                    .append("：").append(u.getSumTotalTokens()).append(" tokens，¥")
                    .append(u.getSumCostCny() == null ? "0" : u.getSumCostCny().toPlainString())
                    .append("\n");
        }
        sb.append("合计：").append(totalTokens).append(" tokens，已花 ¥").append(st.getSpent().toPlainString())
                .append(" / 额度 ¥").append(st.getLimit().toPlainString())
                .append("，剩余 ¥").append(st.getRemaining().toPlainString());
        if (!quotaGuard.enforceEnabled()) {
            sb.append("\n（当前未启用限额拦截，仅统计）");
        }
        return sb.toString();
    }

    private int markRequestsApproved(String userId, String adminId) {
        List<AiQuotaRequest> pending = quotaRequestService.list(new LambdaQueryWrapper<AiQuotaRequest>()
                .eq(AiQuotaRequest::getUserId, userId).eq(AiQuotaRequest::getStatus, "pending"));
        for (AiQuotaRequest r : pending) {
            r.setStatus("approved");
            r.setDecidedBy(adminId);
            r.setDecidedAt(LocalDateTime.now());
        }
        if (!pending.isEmpty()) {
            quotaRequestService.updateBatchById(pending);
        }
        return pending.size();
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        reply(msg, "无权限（仅 owner 可执行）");
        return true;
    }

    private String textOf(BbReceiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : msg.getMessageContentList()) {
            if (c.getData() != null) sb.append(c.getData().toString()).append(" ");
        }
        return sb.toString();
    }

    private void reply(BbReceiveMessage msg, String text) {
        bbReplies.text(msg, text);
    }
}
