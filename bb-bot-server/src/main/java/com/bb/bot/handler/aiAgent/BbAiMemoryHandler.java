package com.bb.bot.handler.aiAgent;

import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.aiAgent.memory.MemoryCommandService;
import com.bb.bot.aiAgent.memory.MemoryCompiler;
import com.bb.bot.aiAgent.memory.MemoryQueryService;
import com.bb.bot.aiAgent.memory.MemorySelector;
import com.bb.bot.aiAgent.memory.SessionTracker;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.constant.BotType;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

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
    private BbReplies replies;

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private MemoryQueryService memoryQueryService;

    @Autowired
    private SessionTracker sessionTracker;

    @Autowired
    private MemoryCompiler memoryCompiler;

    @Autowired
    private MemoryCommandService commandService;

    @Autowired
    private MemorySelector memorySelector;

    private static final Pattern TAIL_RE = Pattern.compile("^/?aiAgent\\.memory\\.tail(?:\\s+(\\d+))?\\s*$");
    private static final Pattern SEARCH_RE = Pattern.compile("^/?aiAgent\\.memory\\.search\\s+(.+)$");
    private static final Pattern CARDS_RE = Pattern.compile("^/?aiAgent\\.memory\\.cards(?:\\s+(\\S+))?\\s*$");
    private static final Pattern ITEM_RE = Pattern.compile("^/?aiAgent\\.memory\\.item\\s+(\\S+)\\s*$");
    private static final Pattern DELETE_RE = Pattern.compile("^/?aiAgent\\.memory\\.delete\\s+(\\S+)\\s*$");
    private static final Pattern SUPERSEDE_RE = Pattern.compile("^/?aiAgent\\.memory\\.supersede\\s+(\\S+)\\s+(\\S+)\\s*$");
    private static final Pattern DEBUG_RE = Pattern.compile("^/?aiAgent\\.memory\\.debug\\s+(.+)$");

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.tail"}, name = "记忆 tail")
    public void tail(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = TAIL_RE.matcher(text(msg));
        int n = (m.matches() && m.group(1) != null) ? Integer.parseInt(m.group(1)) : 20;
        n = Math.min(Math.max(n, 1), 100);
        List<AiMemoryEvent> events = memoryQueryService.tail(n);
        if (events.isEmpty()) { replies.text(msg, "事件流为空"); return; }
        StringBuilder sb = new StringBuilder("最近 ").append(events.size()).append(" 条事件：\n");
        for (AiMemoryEvent e : events) {
            sb.append("[").append(e.getCreatedAt()).append("] ")
                    .append(e.getKind()).append(" / ")
                    .append(StringUtils.defaultIfBlank(e.getUserId(), "-")).append(" / ")
                    .append(abbr(e.getText(), 60))
                    .append("\n");
        }
        replies.text(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.search\\s"}, name = "记忆 search")
    public void search(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = SEARCH_RE.matcher(text(msg));
        if (!m.find()) { replies.text(msg, "用法: /aiAgent.memory.search <关键字>"); return; }
        String kw = m.group(1).trim();
        List<AiMemoryEvent> events = memoryQueryService.searchText(kw, 20);
        if (events.isEmpty()) { replies.text(msg, "没找到包含 \"" + kw + "\" 的事件"); return; }
        StringBuilder sb = new StringBuilder("命中 ").append(events.size()).append(" 条:\n");
        for (AiMemoryEvent e : events) {
            sb.append("- [").append(e.getCreatedAt()).append("] ").append(e.getKind()).append(": ").append(abbr(e.getText(), 80)).append("\n");
        }
        replies.text(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.session", "aiAgent.memory.session"}, name = "查记忆 session")
    public void session(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String sessionId = sessionTracker.attachSessionId(msg.getUserId(), msg.getGroupId(), msg.getBotType());
        replies.text(msg, "当前 session_id = " + sessionId);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.reset", "aiAgent.memory.reset"}, name = "切新 session")
    public void reset(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        int ended = sessionTracker.forceEndCurrent(msg.getUserId(), msg.getGroupId());
        replies.text(msg, "已强制结束 " + ended + " 个 session；下一条消息将进入新 session（蒸馏会异步进行）");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.rebuild", "aiAgent.memory.rebuild"}, name = "重编 memory.md")
    public void rebuild(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String mem = memoryCompiler.rebuildAll(msg.getUserId());
        replies.text(msg, "已强制重编 memory.md，长度 " + (mem == null ? 0 : mem.length()) + " 字符");
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.MATCH,
            keyword = {"/aiAgent.memory.view", "aiAgent.memory.view"}, name = "看 memory.md")
    public void view(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        String mem = memoryCompiler.ensureCompiledMemory(msg.getUserId());
        if (StringUtils.isBlank(mem)) { replies.text(msg, "（暂无记忆）"); return; }
        // 截到 4KB 防止 IM 平台炸
        if (mem.length() > 4000) mem = mem.substring(0, 4000) + "\n...(truncated)";
        replies.text(msg, mem);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.cards"}, name = "记忆卡片索引")
    public void cards(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = CARDS_RE.matcher(text(msg));
        String targetUser = (m.matches() && m.group(1) != null) ? m.group(1) : null;
        List<AiMemoryItem> items = commandService.listForOwner(targetUser, 40);
        if (items.isEmpty()) { replies.text(msg, "没有记忆卡片" + (targetUser != null ? "（user=" + targetUser + "）" : "")); return; }
        StringBuilder sb = new StringBuilder("记忆卡片 ").append(items.size()).append(" 张：\n");
        for (AiMemoryItem it : items) {
            sb.append("- ").append(it.getMemoryKey()).append(" [").append(it.getType()).append('/').append(it.getScope())
                    .append('/').append(it.getStatus()).append("] ").append(abbr(it.getSummary(), 60)).append('\n');
        }
        replies.text(msg, sb.toString().trim());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.item\\s"}, name = "查看记忆卡片")
    public void item(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = ITEM_RE.matcher(text(msg));
        if (!m.find()) { replies.text(msg, "用法: /aiAgent.memory.item <memory_key>"); return; }
        AiMemoryItem it = commandService.getByKey(m.group(1));
        if (it == null) { replies.text(msg, "卡片不存在: " + m.group(1)); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("key: ").append(it.getMemoryKey()).append('\n')
          .append("type/scope/status: ").append(it.getType()).append('/').append(it.getScope()).append('/').append(it.getStatus()).append('\n')
          .append("user/group: ").append(StringUtils.defaultString(it.getUserId(), "-")).append(" / ").append(StringUtils.defaultString(it.getGroupId(), "-")).append('\n')
          .append("summary: ").append(it.getSummary()).append('\n');
        if (StringUtils.isNotBlank(it.getWhy())) sb.append("why: ").append(it.getWhy()).append('\n');
        if (StringUtils.isNotBlank(it.getHowToApply())) sb.append("howToApply: ").append(it.getHowToApply()).append('\n');
        sb.append("conf/imp: ").append(it.getConfidence()).append(" / ").append(it.getImportance()).append('\n')
          .append("expires/lastSeen: ").append(it.getExpiresAt()).append(" / ").append(it.getLastSeenAt());
        if (StringUtils.isNotBlank(it.getSupersededBy())) sb.append('\n').append("supersededBy: ").append(it.getSupersededBy());
        replies.text(msg, sb.toString());
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.delete\\s"}, name = "删除记忆卡片")
    public void delete(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = DELETE_RE.matcher(text(msg));
        if (!m.find()) { replies.text(msg, "用法: /aiAgent.memory.delete <memory_key>"); return; }
        boolean ok = commandService.softDelete(m.group(1));
        replies.text(msg, ok ? ("已删除 " + m.group(1)) : ("卡片不存在: " + m.group(1)));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.supersede\\s"}, name = "替代记忆卡片")
    public void supersede(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = SUPERSEDE_RE.matcher(text(msg));
        if (!m.find()) { replies.text(msg, "用法: /aiAgent.memory.supersede <旧key> <新key>"); return; }
        boolean ok = commandService.supersede(m.group(1), m.group(2));
        replies.text(msg, ok ? ("已将 " + m.group(1) + " 标记为被 " + m.group(2) + " 替代") : ("旧卡片不存在: " + m.group(1)));
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?aiAgent\\.memory\\.debug\\s"}, name = "模拟记忆选择")
    public void debug(BbReceiveMessage msg) {
        if (denyIfNotOwner(msg)) return;
        Matcher m = DEBUG_RE.matcher(text(msg));
        if (!m.find()) { replies.text(msg, "用法: /aiAgent.memory.debug <模拟消息>"); return; }
        String block = memorySelector.composeMemoryBlock(msg.getUserId(), msg.getGroupId(), msg.getBotType(), m.group(1).trim());
        replies.text(msg, StringUtils.isBlank(block) ? "（该消息不会注入任何记忆卡片）" : abbr(block, 3500));
    }

    private boolean denyIfNotOwner(BbReceiveMessage msg) {
        if (authService.isOwner(msg.getUserId())) return false;
        replies.text(msg, "无权限（仅 owner 可执行）");
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
}
