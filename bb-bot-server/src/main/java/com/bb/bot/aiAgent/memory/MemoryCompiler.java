package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.database.aiAgent.entity.AiMemoryEvent;
import com.bb.bot.database.aiAgent.entity.AiMemoryFact;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.service.IAiMemoryEventService;
import com.bb.bot.database.aiAgent.service.IAiMemorySessionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对应 openhanako lib/memory/compile.js 的四阶段编译 + assemble。
 *
 * <p>主要入口：</p>
 * <ul>
 *   <li>{@link #compileSessionSummary(AiMemorySession)} —— session 结束时 LLM 蒸馏 summary，写 ai_memory_session.summary + 抽 Key Facts 进 FactStore</li>
 *   <li>{@link #ensureCompiledMemory(String)} —— 给 (userId) 跑 today/week/longterm/facts/assemble，
 *       fingerprint 防重；返回 memory.md 内容</li>
 *   <li>{@link #rebuildAll(String)} —— 强制重编译（owner 命令用）</li>
 * </ul>
 *
 * <p>编译产物落在 {@code workspaceDir/user/<userId>/compiled/{today,week,longterm,facts,memory}.md}</p>
 */
@Slf4j
@Component
public class MemoryCompiler {

    @Autowired
    private IAiMemorySessionService sessionService;

    @Autowired
    private IAiMemoryEventService eventService;

    @Autowired
    private FactStore factStore;

    @Autowired
    private MemoryExtractor memoryExtractor;

    @Autowired
    private AiChatService aiChatService;

    @Value("${aiAgent.memory.workspaceDir:./memory-workspace}")
    private String workspaceDir;

    @Value("${aiAgent.memory.weekDays:7}")
    private int weekDays;

    @Value("${aiAgent.memory.longtermDays:365}")
    private int longtermDays;

    @Value("${aiAgent.memory.factsSummaryWindow:30}")
    private int factsWindowDays;

    @Value("${aiAgent.memory.memoryMdMaxChars:6000}")
    private int memoryMdMaxChars;

    /** 字节上限：防"行数/字符数没超但字节爆 system prompt"（CJK 一字 3 字节，长串索引尤甚）。 */
    @Value("${aiAgent.memory.memoryMdMaxBytes:25000}")
    private int memoryMdMaxBytes;

    private static final String TRUNCATION_NOTICE =
            "\n\n...(记忆已截断：超出长度上限，部分内容未加载。完整记忆见后台 memory.md)";

    private static final Pattern KEY_FACTS_RE = Pattern.compile(
            "(?ms)^#{1,3}\\s*(重要事实|Key Facts)\\s*$\\s*((?:.|\\n)*?)(?=^#{1,3}\\s|\\z)");

    // =========================================================================
    // Session summary 蒸馏（M8.5 的"驱动力"）
    // =========================================================================

    /**
     * 给一个 session 跑 LLM 蒸馏：从该 session 的 chat events 抽 summary，写回
     * ai_memory_session.summary + 抽 Key Facts 进 FactStore。
     */
    public synchronized void compileSessionSummary(AiMemorySession s) {
        if (s == null || StringUtils.isBlank(s.getSessionId())) return;
        if (StringUtils.isNoneBlank(s.getSummary()) && s.getSummaryCompiledAt() != null) {
            return; // 已经编译过
        }
        try {
            // 拉 session 内所有 chat / chat_reply / agent_cmd / agent_reply 事件
            List<AiMemoryEvent> events = eventService.list(new LambdaQueryWrapper<AiMemoryEvent>()
                    .eq(AiMemoryEvent::getSessionId, s.getSessionId())
                    .in(AiMemoryEvent::getKind, Arrays.asList("chat", "chat_reply", "agent_cmd", "agent_reply"))
                    .orderByAsc(AiMemoryEvent::getCreatedAt));
            if (events.isEmpty()) return;

            String conv = renderConversation(events);
            // 一次 LLM 调用同时产出：摘要 + 重要事实（旧 FactStore 路径）+ 结构化记忆卡片（Phase 2 新路径）
            String prompt = "请用中文简洁总结以下对话，并在最后用 ## 重要事实 标题列出长期值得保留的事实。\n" +
                    "对话开始：\n" + conv + "\n对话结束。\n\n" +
                    "格式：\n## 摘要\n（3-5 句话总结这段对话的主题和结论）\n## 重要事实\n- 事实 1\n- 事实 2\n（仅列时间持久的事实：身份、偏好、长期关系、配置等；不要列工作流程/工具偏好/执行细节）"
                    + memoryExtractor.buildCardPromptSection(s);

            List<ChatMessage> req = new ArrayList<>();
            req.add(ChatMessage.system("你是一个对话摘要器。输出严格遵守用户给的 markdown 结构。"));
            req.add(ChatMessage.user(prompt));

            String answer = aiChatService.chat(req, ModelTier.LIGHT);
            if (StringUtils.isBlank(answer)) {
                log.warn("compileSessionSummary 收到空 LLM 回复 session={}", s.getSessionId());
                return;
            }
            s.setSummary(answer);
            s.setSummaryCompiledAt(LocalDateTime.now());
            sessionService.updateById(s);

            // 从 summary 抽 Key Facts 进 FactStore（旧路径，保留兼容）
            List<String> keyFacts = extractKeyFacts(answer);
            for (String fact : keyFacts) {
                factStore.add(s.getUserId(), fact, autoTags(fact), null, s.getSessionId());
            }

            // 从同一回复抽结构化记忆卡片落库（Phase 2 新路径，与 FactStore 并行）
            memoryExtractor.extractAndPersist(answer, s);

            // 蒸馏完一个 session 后 invalidate compiled 文件，下次 ensure 时重编
            invalidateCompiled(s.getUserId());

            log.info("session={} summary 蒸馏完成，抽出 {} 条 facts", s.getSessionId(), keyFacts.size());
        } catch (Exception e) {
            log.warn("compileSessionSummary 失败 session={}", s.getSessionId(), e);
        }
    }

    /** 后台扫描 ended 但还没 summary 的 session，跑蒸馏。 */
    @Scheduled(fixedDelay = 90_000L, initialDelay = 90_000L)
    public void sweepEndedSessions() {
        try {
            List<AiMemorySession> pending = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                    .isNotNull(AiMemorySession::getEndedAt)
                    .isNull(AiMemorySession::getSummaryCompiledAt)
                    .orderByAsc(AiMemorySession::getEndedAt)
                    .last("limit 5"));
            for (AiMemorySession s : pending) {
                compileSessionSummary(s);
            }
        } catch (Exception e) {
            log.warn("MemoryCompiler.sweepEndedSessions 异常", e);
        }
    }

    // =========================================================================
    // 四阶段 today / week / longterm / facts → assemble memory.md
    // =========================================================================

    /** 检查 fingerprint，确保 (userId) 的 compiled/memory.md 是最新的，并返回内容。 */
    public String ensureCompiledMemory(String userId) {
        try {
            Path root = compiledDir(userId);
            Files.createDirectories(root);
            compileToday(userId, root);
            compileWeek(userId, root);
            compileLongterm(userId, root);
            compileFacts(userId, root);
            return assemble(userId, root);
        } catch (Exception e) {
            log.warn("ensureCompiledMemory 失败 user={}", userId, e);
            return "";
        }
    }

    /** Owner 命令：强制重编译。 */
    public synchronized String rebuildAll(String userId) {
        try {
            Path root = compiledDir(userId);
            Files.createDirectories(root);
            for (String name : new String[]{"today.md", "week.md", "longterm.md", "facts.md", "memory.md"}) {
                MemoryFingerprint.invalidate(root.resolve(name));
            }
            return ensureCompiledMemory(userId);
        } catch (Exception e) {
            log.warn("rebuildAll 失败 user={}", userId, e);
            return "";
        }
    }

    private void compileToday(String userId, Path root) throws Exception {
        Path out = root.resolve("today.md");
        LocalDateTime since = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        List<AiMemorySession> sessions = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                .eq(AiMemorySession::getUserId, userId)
                .ge(AiMemorySession::getStartedAt, since)
                .isNotNull(AiMemorySession::getSummary)
                .orderByAsc(AiMemorySession::getStartedAt));
        String fp = MemoryFingerprint.compute(sessions.stream()
                .map(s -> s.getSessionId() + "|" + s.getSummaryCompiledAt())
                .toList());
        if (sessions.isEmpty()) {
            MemoryFingerprint.invalidate(out);
            return;
        }
        if (MemoryFingerprint.isStillFresh(out, fp)) return;
        StringBuilder body = new StringBuilder("# Today\n");
        for (AiMemorySession s : sessions) {
            body.append("\n### ").append(s.getStartedAt()).append("\n").append(s.getSummary()).append("\n");
        }
        Files.writeString(out, body.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        MemoryFingerprint.save(out, fp);
        log.info("compileToday 完成 user={} sessions={}", userId, sessions.size());
    }

    private void compileWeek(String userId, Path root) throws Exception {
        Path out = root.resolve("week.md");
        LocalDateTime since = LocalDateTime.now().minusDays(weekDays);
        List<AiMemorySession> sessions = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                .eq(AiMemorySession::getUserId, userId)
                .ge(AiMemorySession::getStartedAt, since)
                .isNotNull(AiMemorySession::getSummary)
                .orderByAsc(AiMemorySession::getStartedAt));
        String fp = MemoryFingerprint.compute(sessions.stream()
                .map(s -> s.getSessionId() + "|" + s.getSummaryCompiledAt())
                .toList());
        if (sessions.isEmpty()) {
            MemoryFingerprint.invalidate(out);
            return;
        }
        if (MemoryFingerprint.isStillFresh(out, fp)) return;
        // 简化：直接拼摘要列表（避免每次都跑一次 LLM 综合）
        StringBuilder body = new StringBuilder("# This week\n");
        for (AiMemorySession s : sessions) {
            body.append("\n### ").append(s.getStartedAt()).append("\n").append(s.getSummary()).append("\n");
        }
        // 截断不超过 4KB
        String text = body.toString();
        if (text.length() > 4000) text = text.substring(text.length() - 4000);
        Files.writeString(out, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        MemoryFingerprint.save(out, fp);
    }

    private void compileLongterm(String userId, Path root) throws Exception {
        Path out = root.resolve("longterm.md");
        LocalDateTime since = LocalDateTime.now().minusDays(longtermDays);
        List<AiMemorySession> sessions = sessionService.list(new LambdaQueryWrapper<AiMemorySession>()
                .eq(AiMemorySession::getUserId, userId)
                .ge(AiMemorySession::getStartedAt, since)
                .isNotNull(AiMemorySession::getSummary)
                .orderByDesc(AiMemorySession::getStartedAt)
                .last("limit 50"));
        String fp = MemoryFingerprint.compute(sessions.stream()
                .map(s -> s.getSessionId() + "|" + s.getSummaryCompiledAt())
                .toList());
        if (sessions.isEmpty()) {
            MemoryFingerprint.invalidate(out);
            return;
        }
        if (MemoryFingerprint.isStillFresh(out, fp)) return;
        // 长期上下文跑一次 LLM 浓缩
        StringBuilder src = new StringBuilder();
        for (AiMemorySession s : sessions) {
            src.append("\n").append(s.getStartedAt()).append("\n").append(s.getSummary()).append("\n");
        }
        String prompt = "请把以下多段会话摘要浓缩成一段不超过 400 字的长期用户画像（中文），只保留长期值得知道的东西：\n" + src;
        List<ChatMessage> req = List.of(
                ChatMessage.system("你是用户画像压缩器。"),
                ChatMessage.user(prompt)
        );
        String ans = aiChatService.chat(req, ModelTier.LIGHT);
        if (StringUtils.isBlank(ans)) ans = "(LLM 蒸馏失败，本期 longterm 跳过)";
        Files.writeString(out, "# Long-term context\n\n" + ans, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        MemoryFingerprint.save(out, fp);
    }

    private void compileFacts(String userId, Path root) throws Exception {
        Path out = root.resolve("facts.md");
        LocalDateTime since = LocalDateTime.now().minusDays(factsWindowDays);
        // 直接读 FactStore（注意：FactStore 内事实是 session compileSessionSummary 时进的）
        List<AiMemoryFact> facts = factStore.recentForUser(userId, 100);
        // 截到时间窗
        facts = facts.stream()
                .filter(f -> f.getCreatedAt() == null || f.getCreatedAt().isAfter(since))
                .toList();
        String fp = MemoryFingerprint.compute(facts.stream()
                .map(f -> f.getId() + "|" + f.getCreatedAt())
                .toList());
        if (facts.isEmpty()) {
            MemoryFingerprint.invalidate(out);
            return;
        }
        if (MemoryFingerprint.isStillFresh(out, fp)) return;
        StringBuilder body = new StringBuilder("# Key facts\n");
        for (AiMemoryFact f : facts) {
            body.append("- ").append(f.getFact()).append("\n");
        }
        // 让 LLM dedup + 过滤为时间持久画像（≤ 300 字）
        String prompt = "下面是一些用户的事实候选，请去重 + 仅保留时间持久的用户画像（身份、人格、兴趣、喜恶、" +
                "长期关系、稳定偏好），剔除工作流程/工具偏好/执行细节。中文输出，每条一行，以「- 」开头，总长 ≤ 300 字。\n\n" +
                body;
        List<ChatMessage> req = List.of(
                ChatMessage.system("你是事实过滤器。"),
                ChatMessage.user(prompt)
        );
        String filtered = aiChatService.chat(req, ModelTier.LIGHT);
        if (StringUtils.isBlank(filtered)) filtered = body.toString();
        Files.writeString(out, "# Key facts\n\n" + filtered, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        MemoryFingerprint.save(out, fp);
    }

    /** 把 4 份合并成 memory.md，截到 memoryMdMaxChars。 */
    public String assemble(String userId, Path root) throws Exception {
        Path memory = root.resolve("memory.md");
        StringBuilder sb = new StringBuilder();
        sb.append("## 重要事实\n").append(readOrPlaceholder(root.resolve("facts.md"))).append("\n\n");
        sb.append("## 今天\n").append(readOrPlaceholder(root.resolve("today.md"))).append("\n\n");
        sb.append("## 本周\n").append(readOrPlaceholder(root.resolve("week.md"))).append("\n\n");
        sb.append("## 长期画像\n").append(readOrPlaceholder(root.resolve("longterm.md"))).append("\n");
        String text = sb.toString();
        // 字符 + 字节双限制，超限按优先级 facts > today > week > longterm（正文已倒序拼接）截断并写明提醒
        text = truncateByCharsAndBytes(text);
        Files.writeString(memory, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return text;
    }

    /** 先按字符数截，再按 UTF-8 字节数截（二分定位 char 边界，不切坏多字节字符）；任一触发都追加截断提醒。 */
    String truncateByCharsAndBytes(String text) {
        boolean truncated = false;
        if (text.length() > memoryMdMaxChars) {
            text = text.substring(0, memoryMdMaxChars);
            truncated = true;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > memoryMdMaxBytes) {
            int lo = 0, hi = text.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (text.substring(0, mid).getBytes(StandardCharsets.UTF_8).length <= memoryMdMaxBytes) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            text = text.substring(0, lo);
            truncated = true;
        }
        return truncated ? text + TRUNCATION_NOTICE : text;
    }

    private String readOrPlaceholder(Path p) {
        if (!Files.exists(p)) return "（暂无）";
        try {
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "（读取失败）";
        }
    }

    private Path compiledDir(String userId) {
        return Paths.get(workspaceDir, "user", safe(userId), "compiled").toAbsolutePath().normalize();
    }

    private String safe(String userId) {
        return userId == null ? "_anon" : userId.replaceAll("[^\\w\\-]", "_");
    }

    private void invalidateCompiled(String userId) {
        Path root = compiledDir(userId);
        for (String n : new String[]{"today.md", "week.md", "facts.md"}) {
            MemoryFingerprint.invalidate(root.resolve(n));
        }
    }

    // ---------- helpers ----------

    private String renderConversation(List<AiMemoryEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (AiMemoryEvent e : events) {
            String role = "user".equals(e.getSource()) ? (e.getUserName() == null ? "user" : e.getUserName()) : "bot";
            String text = e.getText() == null ? "" : e.getText().trim();
            if (text.isEmpty()) continue;
            sb.append(role).append(": ").append(text).append("\n");
        }
        return sb.toString();
    }

    private List<String> extractKeyFacts(String summaryMd) {
        Matcher m = KEY_FACTS_RE.matcher(summaryMd);
        if (!m.find()) return Collections.emptyList();
        String block = m.group(2);
        List<String> facts = new ArrayList<>();
        for (String line : block.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("-") || t.startsWith("*")) {
                t = t.substring(1).trim();
            }
            if (!t.isEmpty()) facts.add(t);
        }
        return facts;
    }

    private List<String> autoTags(String fact) {
        Set<String> tags = new HashSet<>();
        // 粗暴：按常见词汇打 tag
        String lower = fact.toLowerCase();
        for (String kw : new String[]{"splatoon", "nso", "telegram", "discord", "qq", "deepseek", "openai", "kimi"}) {
            if (lower.contains(kw)) tags.add(kw);
        }
        return new ArrayList<>(tags);
    }
}
