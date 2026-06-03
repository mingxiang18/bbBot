package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.common.util.aiChat.provider.AiChatService;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelTier;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆选择器（Phase 3，热路径）：每次回复前选出要注入的少量卡片正文。
 *
 * <p><b>延迟是第一约束</b>（QQ 回复 &gt;5s 会重推 → 发两张图）。所以分层：</p>
 * <ol>
 *   <li>结构化过滤（scope 精确匹配，防群记忆漏到私聊/别的群）+ status in (active,stale)</li>
 *   <li>alreadySurfaced 过滤（本会话上轮已注入的让位给新候选）</li>
 *   <li>候选数 ≤ N → 直接全注入，<b>不调模型</b>（覆盖绝大多数轮）</li>
 *   <li>否则文本粗筛，仍 &gt; N 才调【最高档模型】做选择题，<b>带硬超时 + 兜底</b></li>
 * </ol>
 *
 * <p>门控负责"少调"，模型档位负责"调得准"（学 CC：选错记忆会污染整条回复）。</p>
 */
@Slf4j
@Component
public class MemorySelector {

    @Autowired
    private IAiMemoryItemService itemService;

    @Autowired
    private AiChatService aiChatService;

    @Autowired(required = false)
    private MemorySelectionLogger selectionLogger;

    /** 候选数 ≤ 该值直接全注入，不调模型。 */
    @Value("${aiAgent.memory.selectMaxInject:8}")
    private int maxInject;

    /** 调模型选择的硬超时（毫秒），超时即 fallback。 */
    @Value("${aiAgent.memory.selectorTimeoutMs:1500}")
    private long selectorTimeoutMs;

    /** 结构化过滤后参与候选的上限。 */
    @Value("${aiAgent.memory.selectCandidateCap:80}")
    private int candidateCap;

    private final ExecutorService selectorPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mem-selector");
        t.setDaemon(true);
        return t;
    });

    /** convKey -> 上一轮注入过的 memory_key 集合，用于 alreadySurfaced。bounded。 */
    private final Map<String, Set<String>> surfacedCache = new ConcurrentHashMap<>();
    private static final int SURFACED_CACHE_MAX = 2000;

    /**
     * 选出并渲染本轮要注入的记忆块。无可用卡片时返回空串（调用方据此回退旧 memory.md）。
     */
    public String composeMemoryBlock(String userId, String groupId, String platform, String queryText) {
        try {
            List<AiMemoryItem> candidates = loadCandidates(userId, groupId);
            if (candidates.isEmpty()) return "";

            String convKey = convKey(platform, groupId, userId);
            Set<String> surfaced = surfacedCache.getOrDefault(convKey, Collections.emptySet());

            // alreadySurfaced：排除上轮已注入；若排完空了则退回全量（不能因此一条都不给）
            List<AiMemoryItem> pool = candidates.stream()
                    .filter(c -> !surfaced.contains(c.getMemoryKey()))
                    .collect(Collectors.toList());
            if (pool.isEmpty()) pool = candidates;

            List<AiMemoryItem> selected = select(pool, queryText, userId, groupId, platform);
            if (selected.isEmpty()) return "";

            rememberSurfaced(convKey, selected);
            return render(candidates, selected);
        } catch (Exception e) {
            log.warn("MemorySelector 失败 user={} group={}，本轮跳过记忆注入", userId, groupId, e);
            return "";
        }
    }

    // ---- 分层选择 ----

    private List<AiMemoryItem> select(List<AiMemoryItem> pool, String queryText,
                                      String userId, String groupId, String platform) {
        if (pool.size() <= maxInject) {
            return pool;
        }
        // 文本粗筛
        List<AiMemoryItem> narrowed = coarseFilter(pool, queryText);
        List<AiMemoryItem> base = narrowed.isEmpty() ? pool : narrowed;
        if (base.size() <= maxInject) {
            return base;
        }
        // 仍超量 → 调最高档模型做选择题（硬超时 + 兜底）
        List<AiMemoryItem> llm = llmSelectWithTimeout(base, queryText, userId, groupId, platform);
        if (llm != null && !llm.isEmpty()) {
            return llm.size() > maxInject ? llm.subList(0, maxInject) : llm;
        }
        return fallbackTopN(base);
    }

    private List<AiMemoryItem> coarseFilter(List<AiMemoryItem> pool, String queryText) {
        Set<String> tokens = tokenize(queryText);
        if (tokens.isEmpty()) return Collections.emptyList();
        List<AiMemoryItem> hit = new ArrayList<>();
        for (AiMemoryItem c : pool) {
            String st = StringUtils.defaultString(c.getSearchText());
            for (String tk : tokens) {
                if (st.contains(tk)) { hit.add(c); break; }
            }
        }
        return hit;
    }

    private List<AiMemoryItem> llmSelectWithTimeout(List<AiMemoryItem> base, String queryText,
                                                    String userId, String groupId, String platform) {
        Future<List<AiMemoryItem>> f = selectorPool.submit(() -> llmSelect(base, queryText, userId, groupId, platform));
        try {
            return f.get(selectorTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
            log.warn("MemorySelector 模型选择超时/异常({}ms)，fallback topN", selectorTimeoutMs);
            return null;
        }
    }

    private List<AiMemoryItem> llmSelect(List<AiMemoryItem> base, String queryText,
                                         String userId, String groupId, String platform) {
        Map<String, AiMemoryItem> byKey = new LinkedHashMap<>();
        StringBuilder idx = new StringBuilder();
        for (AiMemoryItem c : base) {
            byKey.put(c.getMemoryKey(), c);
            idx.append("- ").append(c.getMemoryKey()).append(" [").append(c.getType()).append("] ")
                    .append(StringUtils.abbreviate(c.getSummary(), 100)).append('\n');
        }
        String prompt = "当前用户消息：" + StringUtils.abbreviate(StringUtils.defaultString(queryText), 300) + "\n\n" +
                "下面是候选长期记忆，请只挑出【确信对回应当前这条消息有帮助】的，最多 " + maxInject + " 条。" +
                "宁可少选不可错选，不确定就不要选。只输出一个 JSON 数组，元素是选中的 key，如 [\"m_xxx\"]。\n\n" +
                idx;
        List<ChatMessage> req = List.of(
                ChatMessage.system("你是记忆选择器，只输出 JSON 数组，不要任何解释。"),
                ChatMessage.user(prompt));
        String answer = aiChatService.chat(req, ModelTier.CHAT);
        List<AiMemoryItem> out = new ArrayList<>();
        try {
            int l = answer.indexOf('['), r = answer.lastIndexOf(']');
            if (l >= 0 && r > l) {
                JSONArray keys = JSON.parseArray(answer.substring(l, r + 1));
                for (int i = 0; i < keys.size(); i++) {
                    AiMemoryItem it = byKey.get(keys.getString(i));
                    if (it != null && !out.contains(it)) out.add(it);
                }
            }
        } catch (Exception e) {
            log.warn("MemorySelector 解析模型选择结果失败：{}", StringUtils.abbreviate(answer, 120));
        }
        if (selectionLogger != null) {
            selectionLogger.log(userId, groupId, queryText, base, out, "CHAT");
        }
        return out;
    }

    /** 兜底：按 importance desc、last_seen desc 取前 N。 */
    private List<AiMemoryItem> fallbackTopN(List<AiMemoryItem> base) {
        return base.stream()
                .sorted((a, b) -> {
                    int ci = Double.compare(imp(b), imp(a));
                    if (ci != 0) return ci;
                    LocalDateTime la = a.getLastSeenAt(), lb = b.getLastSeenAt();
                    if (la == null && lb == null) return 0;
                    if (la == null) return 1;
                    if (lb == null) return -1;
                    return lb.compareTo(la);
                })
                .limit(maxInject)
                .collect(Collectors.toList());
    }

    private static double imp(AiMemoryItem c) {
        return c.getImportance() == null ? 0.0 : c.getImportance().doubleValue();
    }

    // ---- 候选加载（scope 精确隔离） ----

    private List<AiMemoryItem> loadCandidates(String userId, String groupId) {
        boolean inGroup = StringUtils.isNotBlank(groupId);
        // 宽查：global / 本人 / 本群；再在 Java 侧按 scope 精确规则过滤，杜绝跨群泄漏
        List<AiMemoryItem> rows = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                .in(AiMemoryItem::getStatus, List.of(MemoryStatus.ACTIVE.code(), MemoryStatus.STALE.code()))
                .and(q -> {
                    q.eq(AiMemoryItem::getScope, MemoryScope.GLOBAL.code());
                    if (StringUtils.isNotBlank(userId)) q.or().eq(AiMemoryItem::getUserId, userId);
                    if (inGroup) q.or().eq(AiMemoryItem::getGroupId, groupId);
                })
                .orderByDesc(AiMemoryItem::getUpdatedAt)
                .last("limit " + candidateCap));
        List<AiMemoryItem> out = new ArrayList<>();
        for (AiMemoryItem c : rows) {
            if (eligible(c, userId, groupId, inGroup)) out.add(c);
        }
        return out;
    }

    private boolean eligible(AiMemoryItem c, String userId, String groupId, boolean inGroup) {
        MemoryScope scope = MemoryScope.parse(c.getScope());
        if (scope == null) return false;
        return switch (scope) {
            case GLOBAL -> true;
            case USER -> StringUtils.isNotBlank(userId) && userId.equals(c.getUserId());
            case GROUP -> inGroup && groupId.equals(c.getGroupId());
            case USER_IN_GROUP -> inGroup && groupId.equals(c.getGroupId())
                    && StringUtils.isNotBlank(userId) && userId.equals(c.getUserId());
        };
    }

    // ---- 渲染 ----

    private String render(List<AiMemoryItem> index, List<AiMemoryItem> selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- Memory Index ---\n");
        for (AiMemoryItem c : index) {
            sb.append("- ").append(c.getMemoryKey()).append(" [").append(c.getType()).append('/')
                    .append(c.getScope()).append("] ").append(StringUtils.abbreviate(c.getSummary(), 80)).append('\n');
        }
        sb.append("--- Memory Index End ---\n\n--- Selected Memories ---\n");
        for (AiMemoryItem c : selected) {
            sb.append(renderCard(c)).append('\n');
        }
        sb.append("--- Selected Memories End ---\n");
        sb.append(USAGE_RULES);
        return sb.toString();
    }

    private String renderCard(AiMemoryItem c) {
        StringBuilder sb = new StringBuilder();
        sb.append("• [").append(c.getType()).append('/').append(c.getScope()).append("] ").append(c.getSummary());
        if (StringUtils.isNotBlank(c.getWhy())) sb.append("\n  原因：").append(c.getWhy());
        if (StringUtils.isNotBlank(c.getHowToApply())) sb.append("\n  用法：").append(c.getHowToApply());
        String warn = stalenessWarning(c);
        if (warn != null) sb.append("\n  ").append(warn);
        return sb.toString();
    }

    /** 老化提示：stale 强提醒；project_state/ephemeral/reference 满 2 天提醒；稳定项放宽。 */
    private String stalenessWarning(AiMemoryItem c) {
        LocalDateTime ref = c.getLastSeenAt() != null ? c.getLastSeenAt() : c.getCreatedAt();
        long days = ref == null ? 0 : Duration.between(ref, LocalDateTime.now()).toDays();
        boolean isStale = MemoryStatus.STALE.code().equals(c.getStatus());
        MemoryType t = MemoryType.parse(c.getType());
        boolean volatileType = t == MemoryType.PROJECT_STATE || t == MemoryType.EPHEMERAL_EVENT || t == MemoryType.REFERENCE;
        if (isStale) {
            return "⚠ 这条已标记可能过期（保存于 " + days + " 天前），使用前请结合当前上下文核实。";
        }
        if (volatileType && days >= 2) {
            return "⚠ 这条保存于 " + days + " 天前，可能已变，据此行动前先核实。";
        }
        return null;
    }

    private void rememberSurfaced(String convKey, List<AiMemoryItem> selected) {
        if (surfacedCache.size() > SURFACED_CACHE_MAX) {
            surfacedCache.clear(); // 简单防膨胀；alreadySurfaced 丢失只是偶发重复注入，无正确性影响
        }
        surfacedCache.put(convKey, selected.stream().map(AiMemoryItem::getMemoryKey)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String convKey(String platform, String groupId, String userId) {
        return StringUtils.defaultString(platform) + "|" + StringUtils.defaultString(groupId) + "|" + StringUtils.defaultString(userId);
    }

    private static Set<String> tokenize(String q) {
        String norm = FactStore.normalize(q);
        if (StringUtils.isBlank(norm)) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (String w : norm.split("\\s+")) {
            if (w.length() >= 2) out.add(w);
        }
        return out;
    }

    private static final String USAGE_RULES =
            "\n记忆使用规则：\n" +
            "- 自然地融入回复，不要主动声明\"根据记忆\"或\"你以前说过\"；只有用户问起过去/是否记得/记忆可能过期时才说明来源与时间。\n" +
            "- scope 不匹配的记忆不得使用。\n" +
            "- 带过期提醒的记忆要谨慎：其中提到的具体事实（某 API/决定/文件/flag）在据此行动前先核实，\"记忆说 X 存在\"不等于\"X 现在存在\"。\n";
}
