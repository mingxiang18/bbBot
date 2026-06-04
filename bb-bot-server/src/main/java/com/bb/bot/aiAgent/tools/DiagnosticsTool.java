package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.common.util.aiChat.billing.GlobalUsageGuard;
import com.bb.bot.diagnostics.MessageTrace;
import com.bb.bot.diagnostics.MessageTraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bot 自查工具（owner 专用）：让 owner 用自然语言问「你最近正常吗 / 我刚才那条为什么没回」，
 * 模型自动调用本工具拿到结构化诊断快照，再转人话解释。
 *
 * <p>数据全部来自 {@link MessageTraceRecorder} 的内存环形缓冲，即便 DB 故障也能即时回答。</p>
 */
@Slf4j
@Component
public class DiagnosticsTool {

    @Autowired
    private MessageTraceRecorder messageTraceRecorder;

    /** 全局每日 token 守卫；可能未启用，故允许缺省。 */
    @Autowired(required = false)
    private GlobalUsageGuard globalUsageGuard;

    @AiTool(
            name = "self_check",
            description = "自查机器人近况：返回最近一段时间收到/回复/跳过/失败的消息统计、跳过原因分布、"
                    + "AI token 用量等健康快照。当 owner 问「你最近正常吗 / 怎么不爱说话了 / 还活着吗 / "
                    + "最近有没有问题」时调用，据此判断是没收到、被跳过还是发送失败。",
            requiresOwner = true)
    public Map<String, Object> selfCheck(
            @AiToolParam(name = "minutes", description = "统计窗口（分钟），默认 60", required = false)
            Integer minutes) {
        int window = minutes == null || minutes <= 0 ? 60 : minutes;
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> activity = messageTraceRecorder.stats(window);
            result.put("recentActivity", activity);
            result.put("bufferedTraces", messageTraceRecorder.bufferedCount());

            // 简单健康判读，给 LLM 一个结论性提示
            int received = asInt(activity.get("received"));
            int replied = asInt(activity.get("replied"));
            int failed = asInt(activity.get("failed"));
            String verdict;
            if (received == 0) {
                verdict = "近" + window + "分钟未收到任何消息（可能确实没人发，也可能入站链路异常）";
            } else if (failed > 0) {
                verdict = "有 " + failed + " 条回复发送失败，需关注发送出口";
            } else if (replied == 0) {
                verdict = "收到 " + received + " 条但一条都没回（多半是群未开自动回复或概率未命中，看 skipReasons）";
            } else {
                verdict = "收发正常";
            }
            result.put("verdict", verdict);

            if (globalUsageGuard != null) {
                Map<String, Object> token = new LinkedHashMap<>();
                token.put("tokensToday", globalUsageGuard.tokensToday());
                token.put("dailyLimit", globalUsageGuard.dailyLimit());
                token.put("overDailyLimit", globalUsageGuard.isOverDailyLimit());
                result.put("aiToken", token);
            }
            return result;
        } catch (Exception e) {
            log.warn("self_check 失败", e);
            result.put("error", "self_check_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    @AiTool(
            name = "trace_message",
            description = "回溯最近消息的处理轨迹，逐条说明「收到→是否重复→命中哪个处理器→决策(回复/跳过及原因)→"
                    + "是否发出」。当 owner 问「我刚才发的怎么没回 / 某条消息为什么没反应 / 你收到我消息了吗」时调用。"
                    + "可按用户、时间范围、关键字过滤。",
            requiresOwner = true)
    public Map<String, Object> traceMessage(
            @AiToolParam(name = "userId", description = "只看某用户的消息（QQ 号/openid），不传则不限", required = false)
            String userId,
            @AiToolParam(name = "minutesAgo", description = "只看最近多少分钟内的消息，不传则不限", required = false)
            Integer minutesAgo,
            @AiToolParam(name = "keyword", description = "只看正文包含该关键字的消息，不传则不限", required = false)
            String keyword,
            @AiToolParam(name = "limit", description = "最多返回多少条，默认 10", required = false)
            Integer limit) {
        int n = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<MessageTrace> traces = messageTraceRecorder.recentMatching(
                    blankToNull(userId), minutesAgo, blankToNull(keyword), n);
            List<Map<String, Object>> items = new ArrayList<>();
            for (MessageTrace t : traces) {
                items.add(t.toMap());
            }
            result.put("count", items.size());
            result.put("traces", items);
            if (items.isEmpty()) {
                result.put("hint", "缓冲区里没有匹配的消息轨迹（可能已被新消息挤出、时间窗太窄，或这条消息根本没进入站链路）");
            }
            return result;
        } catch (Exception e) {
            log.warn("trace_message 失败", e);
            result.put("error", "trace_message_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    private static int asInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
