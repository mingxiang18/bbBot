package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.aiAgent.memory.MemoryCompiler;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.aiAgent.memory.SessionTracker;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owner 专用：记忆系统管理命令。
 *
 * <ul>
 *   <li>{@code /aiAgent.memory.tail [N]} —— 看最近 N 条事件（默认 20）</li>
 *   <li>{@code /aiAgent.memory.search <kw>} —— 在事件文本里 grep</li>
 *   <li>{@code /aiAgent.memory.session} —— 看自己当前 session 状态</li>
 *   <li>{@code /aiAgent.memory.reset} —— 强制切新 session（让 sweep 蒸馏当前 session）</li>
 *   <li>{@code /aiAgent.memory.rebuild} —— 强制重编 4 阶段 memory.md</li>
 *   <li>{@code /aiAgent.memory.view} —— 读自己当前 memory.md</li>
 * </ul>
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "AI 记忆管理")
public class BbAiMemoryHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private MemoryQueryService memoryQueryService;

    @Autowired
    private SessionTracker sessionTracker;

    @Autowired
    private MemoryCompiler memoryCompiler;

    private static final Pattern TAIL_RE = Pattern.compile("^/?aiAgent\\.memory\\.tail(?:\\s+(\\d+))?\\s*$");
    private static final Pattern SEARCH_RE = Pattern.compile("^/?aiAgent\\.memory\\.search\\s+(.+)$");

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.tail"}, name = "记忆 tail")
    public void tail(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = TAIL_RE.matcher(text(msg));
        int n = (m.matches() && m.group(1) != null) ? Integer.parseInt(m.group(1)) : 20;
        n = Math.min(Math.max(n, 1), 100);
        List<AiMemoryEvent> events = memoryQueryService.tail(n);
        if (events.isEmpty()) { reply(msg, "事件流为空"); return; }
        StringBuilder sb = new StringBuilder("最近 ").append(events.size()).append(" 条事件：\n");
        for (AiMemoryEvent e : events) {
            sb.append("[").append(e.getCreatedAt()).append("] ")
                    .append(e.getKind()).append(" / ")
                    .append(StringUtils.defaultIfBlank(e.getUserId(), "-")).append(" / ")
                    .append(abbr(e.getText(), 60))
                    .append("\n");
        }
        reply(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.search\\s"}, name = "记忆 search")
    public void search(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = SEARCH_RE.matcher(text(msg));
        if (!m.find()) { reply(msg, "用法: /aiAgent.memory.search <关键字>"); return; }
        String kw = m.group(1).trim();
        List<AiMemoryEvent> events = memoryQueryService.searchText(kw, 20);
        if (events.isEmpty()) { reply(msg, "没找到包含 \"" + kw + "\" 的事件"); return; }
        StringBuilder sb = new StringBuilder("命中 ").append(events.size()).append(" 条:\n");
        for (AiMemoryEvent e : events) {
            sb.append("- [").append(e.getCreatedAt()).append("] ").append(e.getKind()).append(": ").append(abbr(e.getText(), 80)).append("\n");
        }
        reply(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.session", "aiAgent.memory.session"}, name = "查记忆 session")
    public void session(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String sessionId = sessionTracker.attachSessionId(msg.getUserId(), msg.getGroupId(), msg.getBotType());
        reply(msg, "当前 session_id = " + sessionId);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.reset", "aiAgent.memory.reset"}, name = "切新 session")
    public void reset(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        int ended = sessionTracker.forceEndCurrent(msg.getUserId(), msg.getGroupId());
        reply(msg, "已强制结束 " + ended + " 个 session；下一条消息将进入新 session（蒸馏会异步进行）");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.rebuild", "aiAgent.memory.rebuild"}, name = "重编 memory.md")
    public void rebuild(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String mem = memoryCompiler.rebuildAll(msg.getUserId());
        reply(msg, "已强制重编 memory.md，长度 " + (mem == null ? 0 : mem.length()) + " 字符");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.view", "aiAgent.memory.view"}, name = "看 memory.md")
    public void view(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String mem = memoryCompiler.ensureCompiledMemory(msg.getUserId());
        if (StringUtils.isBlank(mem)) { reply(msg, "（暂无记忆）"); return; }
        // 截到 4KB 防止 IM 平台炸
        if (mem.length() > 4000) mem = mem.substring(0, 4000) + "\n...(truncated)";
        reply(msg, mem);
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        reply(msg, "无权限（仅 owner 可执行）");
        return true;
    }

    private String text(BbReceiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        for (BbMessageContent c : msg.getMessageContentList()) {
            if (c.getData() != null) sb.append(c.getData().toString()).append(" ");
        }
        return sb.toString().trim();
    }

    private String abbr(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private void reply(BbReceiveMessage msg, String text) {
        BbSendMessage send = new BbSendMessage(msg);
        send.setMessageList(Collections.singletonList(BbMessageContent.buildTextContent(text)));
        bbMessageApi.sendMessage(send);
    }
}
