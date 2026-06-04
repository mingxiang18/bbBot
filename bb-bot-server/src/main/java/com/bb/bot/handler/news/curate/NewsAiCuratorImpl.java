package com.bb.bot.handler.news.curate;

import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AIProviderProperties;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelSpec;
import com.bb.bot.common.util.aiChat.provider.ProviderDispatcher;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsAiCurator;
import com.bb.bot.handler.news.contract.NewsCategory;
import com.bb.bot.handler.news.contract.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 整理实现（T3）。调用项目现有 LLM 完成语义聚类去重、中文摘要、分类、重要性、今日速览。
 *
 * <p>LLM 不可用（未启用 / 未配置 / 调用异常 / 解析失败）时一律降级：原始条目直接映射为
 * {@link CuratedItem}，brief 空，绝不抛异常导致流水线失败。</p>
 */
@Slf4j
@Component
public class NewsAiCuratorImpl implements NewsAiCurator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private ProviderDispatcher providerDispatcher;

    @Autowired
    private AIProviderProperties aiProviderProperties;

    @Autowired
    private NewsConfig newsConfig;

    @Override
    public DailyReport curate(List<NewsItem> items) {
        List<NewsItem> input = items == null ? List.of() : items;
        NewsConfig.Ai aiCfg = newsConfig.getAi();

        // 降级：AI 未启用
        if (aiCfg == null || !aiCfg.isEnabled()) {
            log.info("[news-curate] AI 整理未启用，走降级。条目数={}", input.size());
            return fallback(input);
        }

        // 选模型
        ModelSpec spec = resolveModel(aiCfg.getRole());
        if (spec == null || !spec.isConfigured()) {
            log.warn("[news-curate] 未找到可用模型（role={}），走降级。", aiCfg == null ? null : aiCfg.getRole());
            return fallback(input);
        }

        // 按源轮转后再取前 maxItems 条：原始 input 是各源顺序拼接的，直接 subList(0,maxItems)
        // 会让前 1~2 个源占满名额、后面所有源/分类被饿死（AI 根本看不到）。轮转保证截断后
        // 每个源/分类都有代表，AI 才能在全量里做有意义的精选。
        int maxItems = aiCfg.getMaxItems() > 0 ? aiCfg.getMaxItems() : input.size();
        List<NewsItem> balanced = interleaveBySource(input);
        List<NewsItem> picked = balanced.size() > maxItems ? balanced.subList(0, maxItems) : balanced;
        if (picked.isEmpty()) {
            return fallback(input);
        }

        // Phase 3：给每条分配稳定 id（n1..nN），LLM 只引用 id，服务端按 id 回填真实字段
        Map<String, NewsItem> byId = assignIds(picked);

        // 调 LLM
        log.info("[news-curate] 喂入 LLM 候选 {} 条（轮转截断自 {} 条，model={}）",
                byId.size(), input.size(), spec.getModel());
        String raw;
        try {
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(CuratePrompt.system()),
                    ChatMessage.user(CuratePrompt.user(byId))
            );
            raw = providerDispatcher.chat(spec, messages);
        } catch (AIException e) {
            log.warn("[news-curate] LLM 调用失败，走降级：{}", e.getMessage());
            return fallback(input);
        } catch (Exception e) {
            log.warn("[news-curate] LLM 调用异常，走降级", e);
            return fallback(input);
        }

        // 原始输出留痕（debug 级，便于排查 AI 选取逻辑；正常 info 不刷全文）
        log.debug("[news-curate] LLM 原始输出：{}", raw);

        // 鲁棒解析
        CurateResponse resp = CurateResponse.parse(raw);
        if (resp == null) {
            log.warn("[news-curate] LLM 输出解析失败，走降级。原始片段={}",
                    raw == null ? null : StringUtils.left(raw, 200));
            return fallback(input);
        }

        DailyReport report = assemble(resp, byId, input);
        log.info("[news-curate] AI 精选完成：喂入 {} 条 → 最终保留 {} 条", byId.size(),
                report == null || report.items() == null ? 0 : report.items().size());
        return report;
    }

    /** 给候选条目分配稳定 id（n1..nN），保持喂入顺序。 */
    static Map<String, NewsItem> assignIds(List<NewsItem> picked) {
        Map<String, NewsItem> byId = new LinkedHashMap<>();
        int i = 1;
        for (NewsItem it : picked) {
            byId.put("n" + (i++), it);
        }
        return byId;
    }

    /**
     * 根据角色解析模型：light 为空时回退 heavy，再从 models 表取 spec。
     */
    private ModelSpec resolveModel(String role) {
        AIProviderProperties.Roles roles = aiProviderProperties.getRoles();
        if (roles == null) {
            return null;
        }
        String name;
        if ("heavy".equalsIgnoreCase(role)) {
            name = roles.getHeavy();
        } else {
            // 默认 light，light 缺省回退 heavy
            name = StringUtils.firstNonBlank(roles.getLight(), roles.getHeavy());
        }
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return aiProviderProperties.getModels().get(name);
    }

    /**
     * 按源轮转（round-robin）重排：把"各源顺序拼接"的列表，重排成"每轮各源各取一条"。
     * 例如 [澎湃1,澎湃2,中新1,中新2,BBC1] → [澎湃1,中新1,BBC1,澎湃2,中新2]。
     * 这样后续 subList(0,maxItems) 截断时，每个源/分类都能进入 AI 视野，而不是被前几个源占满。
     * 各源内部相对顺序保持不变。
     */
    static List<NewsItem> interleaveBySource(List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        // 按源分组（保持首次出现顺序 + 组内顺序）
        Map<String, Deque<NewsItem>> bySource = new LinkedHashMap<>();
        for (NewsItem it : items) {
            String key = it.sourceName() == null ? "" : it.sourceName();
            bySource.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(it);
        }
        List<NewsItem> out = new ArrayList<>(items.size());
        boolean any = true;
        while (any) {
            any = false;
            for (Deque<NewsItem> q : bySource.values()) {
                NewsItem it = q.pollFirst();
                if (it != null) {
                    out.add(it);
                    any = true;
                }
            }
        }
        return out;
    }

    /** 摘要最大长度（防超长）。 */
    private static final int MAX_SUMMARY_LEN = 140;

    /**
     * 组装正常结果（Phase 3）：按 id 回填真实 title/link/sourceName/english，校验未知/重复 id、
     * 非法分类、空/超长摘要；再 normalize 分类、按 (分类顺序, importance 倒序) 排序。
     *
     * @param byId  喂给 LLM 的 id → 真实条目映射（回填来源）
     * @param input 全量候选（用于统计 sourceCount）
     */
    private DailyReport assemble(CurateResponse resp, Map<String, NewsItem> byId, List<NewsItem> input) {
        List<CuratedItem> curated = new ArrayList<>();
        Set<String> usedIds = new java.util.HashSet<>();
        int unknown = 0;
        int dup = 0;
        for (CurateResponse.Item it : resp.getItems()) {
            if (it == null) {
                continue;
            }
            String id = it.getId();
            NewsItem src = id == null ? null : byId.get(id);
            if (src == null) {
                // 未知 / 缺失 id：LLM 凭空造出来的条目，丢弃（核心防幻觉）
                unknown++;
                continue;
            }
            if (!usedIds.add(id)) {
                dup++;
                continue;
            }
            String category = NewsCategory.normalize(it.getCategory());
            int importance = clampImportance(it.getImportance());
            int mergedCount = clusterCount(it.getClusterIds());
            String note = it.getNote() == null ? "" : it.getNote();
            String summary = sanitizeSummary(it.getSummaryZh(), src);
            boolean english = "en".equalsIgnoreCase(src.lang());
            // title/link/sourceName 全部来自服务端回填，绝不采信 LLM 输出
            curated.add(new CuratedItem(
                    src.title(),
                    src.link(),
                    src.sourceName(),
                    category,
                    summary,
                    importance,
                    english,
                    mergedCount,
                    note
            ));
        }
        if (unknown > 0 || dup > 0) {
            log.warn("[news-curate] 校验丢弃：未知/缺失 id={} 条，重复 id={} 条", unknown, dup);
        }

        sortItems(curated);

        String brief = resp.getBrief() == null ? "" : resp.getBrief();
        return new DailyReport(
                today(),
                brief,
                curated,
                countSources(input),
                curated.size()
        );
    }

    /** clusterIds 数量即合并条目数；为空按 1（未合并）。 */
    private static int clusterCount(List<String> clusterIds) {
        return clusterIds == null || clusterIds.isEmpty() ? 1 : clusterIds.size();
    }

    /** 摘要清洗：空则回退原文摘要再回退标题；超长截断。 */
    private static String sanitizeSummary(String summary, NewsItem src) {
        String s = summary == null ? "" : summary.trim();
        if (s.isEmpty()) {
            s = src.description() == null ? "" : src.description().trim();
        }
        if (s.isEmpty()) {
            s = src.title() == null ? "" : src.title().trim();
        }
        if (s.length() > MAX_SUMMARY_LEN) {
            s = s.substring(0, MAX_SUMMARY_LEN) + "…";
        }
        return s;
    }

    /** 降级页面的导语标记，供页面显式提示"非 AI 精选"。 */
    public static final String FALLBACK_BRIEF = "⚠️ AI 整理降级：以下为按源限量挑选的原文摘要，未经语义精选";

    /**
     * 降级结果：<b>保守</b>映射，不再 raw 全量出页。
     *
     * <p>规则：按源轮转后，逐条过滤——标题非空、链接合法（http/https）、不含低价值关键词；
     * 每源最多 {@code fallbackPerSource} 条、总数不超过 {@code fallbackMaxItems}。
     * brief 置为 {@link #FALLBACK_BRIEF} 以便页面标记降级来源。</p>
     */
    private DailyReport fallback(List<NewsItem> input) {
        NewsConfig.Ai ai = newsConfig.getAi();
        int perSource = ai != null && ai.getFallbackPerSource() > 0 ? ai.getFallbackPerSource() : 2;
        int maxItems = ai != null && ai.getFallbackMaxItems() > 0 ? ai.getFallbackMaxItems() : 10;
        List<String> lowValue = ai == null || ai.getLowValueKeywords() == null
                ? List.of() : ai.getLowValueKeywords();

        List<CuratedItem> curated = new ArrayList<>();
        Map<String, Integer> perSourceCount = new LinkedHashMap<>();
        // 复用按源轮转，保证降级名额也均摊到各源而非被前几个源占满
        for (NewsItem item : interleaveBySource(input)) {
            if (curated.size() >= maxItems) {
                break;
            }
            if (StringUtils.isBlank(item.title()) || !isValidLink(item.link())) {
                continue;
            }
            if (containsLowValue(item.title(), lowValue)) {
                continue;
            }
            String src = item.sourceName() == null ? "" : item.sourceName();
            int used = perSourceCount.getOrDefault(src, 0);
            if (used >= perSource) {
                continue;
            }
            perSourceCount.put(src, used + 1);
            curated.add(new CuratedItem(
                    item.title(),
                    item.link(),
                    item.sourceName(),
                    NewsCategory.normalize(item.category()),
                    item.description(),
                    3,
                    "en".equalsIgnoreCase(item.lang()),
                    1,
                    ""
            ));
        }
        sortItems(curated);
        return new DailyReport(
                today(),
                FALLBACK_BRIEF,
                curated,
                countSources(input),
                curated.size()
        );
    }

    /** 链接是否为合法 http/https 绝对地址。 */
    private static boolean isValidLink(String link) {
        if (StringUtils.isBlank(link)) {
            return false;
        }
        String l = link.trim().toLowerCase();
        return l.startsWith("http://") || l.startsWith("https://");
    }

    /** 标题是否命中任一低价值关键词。 */
    private static boolean containsLowValue(String title, List<String> keywords) {
        if (title == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (StringUtils.isNotBlank(kw) && title.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 按分类顺序（NewsCategory.ALL）升序、importance 倒序排序。 */
    private void sortItems(List<CuratedItem> items) {
        items.sort(Comparator
                .comparingInt((CuratedItem c) -> categoryOrder(c.category()))
                .thenComparing(Comparator.comparingInt(CuratedItem::importance).reversed()));
    }

    private int categoryOrder(String category) {
        int idx = NewsCategory.ALL.indexOf(category);
        return idx < 0 ? NewsCategory.ALL.size() : idx;
    }

    private int clampImportance(int importance) {
        if (importance < 1) {
            return 1;
        }
        return Math.min(importance, 5);
    }

    private int countSources(List<NewsItem> input) {
        Set<String> names = new LinkedHashSet<>();
        for (NewsItem item : input) {
            if (item.sourceName() != null) {
                names.add(item.sourceName());
            }
        }
        return names.size();
    }

    private String today() {
        return LocalDate.now().format(DATE_FMT);
    }
}
