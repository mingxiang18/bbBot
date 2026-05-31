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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

        // 取前 maxItems 条
        int maxItems = aiCfg.getMaxItems() > 0 ? aiCfg.getMaxItems() : input.size();
        List<NewsItem> picked = input.size() > maxItems ? input.subList(0, maxItems) : input;
        if (picked.isEmpty()) {
            return fallback(input);
        }

        // 调 LLM
        String raw;
        try {
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(CuratePrompt.system()),
                    ChatMessage.user(CuratePrompt.user(picked))
            );
            raw = providerDispatcher.chat(spec, messages);
        } catch (AIException e) {
            log.warn("[news-curate] LLM 调用失败，走降级：{}", e.getMessage());
            return fallback(input);
        } catch (Exception e) {
            log.warn("[news-curate] LLM 调用异常，走降级", e);
            return fallback(input);
        }

        // 鲁棒解析
        CurateResponse resp = CurateResponse.parse(raw);
        if (resp == null) {
            log.warn("[news-curate] LLM 输出解析失败，走降级。原始片段={}",
                    raw == null ? null : StringUtils.left(raw, 200));
            return fallback(input);
        }

        return assemble(resp, input);
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
     * 组装正常结果：normalize 分类、按 (分类顺序, importance 倒序) 排序，统计 source/total。
     */
    private DailyReport assemble(CurateResponse resp, List<NewsItem> input) {
        List<CuratedItem> curated = new ArrayList<>();
        for (CurateResponse.Item it : resp.getItems()) {
            if (it == null) {
                continue;
            }
            String category = NewsCategory.normalize(it.getCategory());
            int importance = clampImportance(it.getImportance());
            int mergedCount = it.getMergedCount() > 0 ? it.getMergedCount() : 1;
            String note = it.getNote() == null ? "" : it.getNote();
            curated.add(new CuratedItem(
                    it.getTitle(),
                    it.getLink(),
                    it.getSourceName(),
                    category,
                    it.getSummaryZh(),
                    importance,
                    it.isEnglish(),
                    mergedCount,
                    note
            ));
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

    /**
     * 降级结果：原始条目直接映射，brief 空。
     */
    private DailyReport fallback(List<NewsItem> input) {
        List<CuratedItem> curated = new ArrayList<>();
        for (NewsItem item : input) {
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
                "",
                curated,
                countSources(input),
                curated.size()
        );
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
