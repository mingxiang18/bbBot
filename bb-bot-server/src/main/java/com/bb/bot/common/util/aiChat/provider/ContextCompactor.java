package com.bb.bot.common.util.aiChat.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话内上下文压缩器。
 *
 * <p>{@link ToolLoopExecutor} 在每步调用 LLM 前调用本组件：当累计上下文超过
 * {@code aiAgent.compact.thresholdChars} 时，把早期消息蒸馏成一段摘要，只保留
 * system 头 + 末尾约 {@code keepChars} 字符的近期消息，避免长任务撑爆模型上下文。</p>
 *
 * <p>切点保证 tool 配对完整：suffix 不会以孤立的 TOOL 消息开头（其对应的
 * assistant tool_calls 会连同被划入 middle 一起压缩）。</p>
 */
@Slf4j
@Component
public class ContextCompactor {

    @Autowired
    private AiChatService aiChatService;

    /** 上下文估算字符数超过此值触发压缩。 */
    @Value("${aiAgent.compact.thresholdChars:40000}")
    private int thresholdChars;

    /** 压缩后保留的近期消息字符预算。 */
    @Value("${aiAgent.compact.keepChars:12000}")
    private int keepChars;

    /**
     * 按需压缩。未超阈值或无可压缩中段时原样返回。
     */
    public List<ChatMessage> compactIfNeeded(List<ChatMessage> messages) {
        if (messages == null || messages.size() < 4) {
            return messages;
        }
        if (estimateChars(messages) <= thresholdChars) {
            return messages;
        }
        return compact(messages);
    }

    private List<ChatMessage> compact(List<ChatMessage> messages) {
        // 1. 剥离开头连续的 SYSTEM 消息
        int firstBody = 0;
        while (firstBody < messages.size() && messages.get(firstBody).getRole() == ChatMessage.Role.SYSTEM) {
            firstBody++;
        }
        List<ChatMessage> systemMsgs = messages.subList(0, firstBody);
        List<ChatMessage> body = messages.subList(firstBody, messages.size());

        // 2. 从尾部回退累计 keepChars，定出 suffix 起点
        int acc = 0;
        int start = body.size();
        for (int i = body.size() - 1; i >= 0; i--) {
            acc += estimateOne(body.get(i));
            start = i;
            if (acc >= keepChars) {
                break;
            }
        }
        // 3. suffix 不能以孤立 TOOL 消息开头：往后挪到第一个非 TOOL
        while (start < body.size() && body.get(start).getRole() == ChatMessage.Role.TOOL) {
            start++;
        }
        if (start <= 0 || start >= body.size()) {
            // 没有可压缩的中段（近期消息已占满预算 / 全是 tool 尾巴）
            return messages;
        }

        List<ChatMessage> middle = new ArrayList<>(body.subList(0, start));
        List<ChatMessage> suffix = body.subList(start, body.size());

        String summary = summarize(middle);

        List<ChatMessage> out = new ArrayList<>(systemMsgs.size() + 1 + suffix.size());
        out.addAll(systemMsgs);
        out.add(ChatMessage.user("【以下是早期对话的压缩摘要，请据此延续，不要重复已完成的步骤】\n" + summary));
        out.addAll(suffix);
        log.info("上下文压缩：{} 条 → {} 条（middle {} 条蒸馏为摘要）",
                messages.size(), out.size(), middle.size());
        return out;
    }

    /** 调 LLM 把中段对话蒸馏成摘要；失败则降级为占位文本，保证请求仍合法。 */
    private String summarize(List<ChatMessage> middle) {
        String rendered = renderForSummary(middle);
        List<ChatMessage> req = new ArrayList<>();
        req.add(ChatMessage.system("你是对话历史压缩器。把下面的多轮对话（含工具调用与结果）压缩成一段简洁、"
                + "信息完整的摘要：保留关键事实、已完成的步骤、工具返回的重要数据、尚未解决的问题与待办。"
                + "用简体中文，直接给摘要，不要寒暄。"));
        req.add(ChatMessage.user(rendered));
        try {
            String summary = aiChatService.chat(req, ModelTier.LIGHT);
            if (StringUtils.isNotBlank(summary)) {
                return summary;
            }
        } catch (Exception e) {
            log.warn("上下文压缩摘要调用失败，降级为占位文本", e);
        }
        return "[早期对话内容较长，已省略。如需细节请向用户确认。]";
    }

    private String renderForSummary(List<ChatMessage> middle) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : middle) {
            sb.append(roleLabel(m.getRole())).append(": ").append(textOf(m));
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                sb.append(" [调用工具:");
                for (ToolCall tc : m.getToolCalls()) {
                    sb.append(' ').append(tc.getName());
                }
                sb.append(']');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String roleLabel(ChatMessage.Role role) {
        switch (role) {
            case USER: return "用户";
            case ASSISTANT: return "助手";
            case TOOL: return "工具结果";
            default: return "系统";
        }
    }

    private String textOf(ChatMessage m) {
        StringBuilder sb = new StringBuilder();
        for (MessageContent c : m.getContents()) {
            if (c.getType() == MessageContent.Type.TEXT && c.getValue() != null) {
                sb.append(c.getValue());
            } else {
                sb.append("[图片]");
            }
        }
        return sb.toString();
    }

    private int estimateChars(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage m : messages) {
            total += estimateOne(m);
        }
        return total;
    }

    /** 单条消息的字符估算：文本按长度，图片按固定成本，外加 reasoning / tool_calls 参数。 */
    private int estimateOne(ChatMessage m) {
        int n = 0;
        for (MessageContent c : m.getContents()) {
            if (c.getType() == MessageContent.Type.TEXT) {
                n += c.getValue() == null ? 0 : c.getValue().length();
            } else {
                n += 800;
            }
        }
        if (m.getReasoningContent() != null) {
            n += m.getReasoningContent().length();
        }
        if (m.getToolCalls() != null) {
            for (ToolCall tc : m.getToolCalls()) {
                n += tc.getArgumentsJson() == null ? 0 : tc.getArgumentsJson().length();
            }
        }
        return n;
    }
}
