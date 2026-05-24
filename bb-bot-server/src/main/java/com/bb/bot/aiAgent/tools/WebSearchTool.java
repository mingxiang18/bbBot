package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.search.WebSearchProvider;
import com.bb.bot.aiAgent.search.WebSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用原语：搜索引擎查询。
 *
 * <p>后端做成 {@link WebSearchProvider} 插件化 + 优先级降级（参考 OpenClaw）：
 * 把所有 provider 按 priority 升序排，取第一个就绪的执行；抛异常或返回空则降级到
 * 下一个。优先级：Brave(10) → Tavily(20) → SerpAPI(30) → DuckDuckGo(100, 兜底)。</p>
 *
 * <p>配了哪个后端的 key 就启用哪个；都不配则退化到免 key 的 DuckDuckGo。</p>
 */
@Slf4j
@Component
public class WebSearchTool {

    private static final int MAX_RESULTS = 5;

    /** Spring 注入全部 WebSearchProvider bean。 */
    @Autowired
    private List<WebSearchProvider> providers;

    @AiTool(
            name = "web_search",
            description = "用搜索引擎搜公网信息。用户问「最新 / 现在 / 实时」的事，" +
                    "或你不知道某个名词时调本工具。返回最多 5 条结果（title / url / snippet）。" +
                    "拿到 url 后通常再用 http_fetch 把感兴趣的页面正文抓回来读" +
                    "（若结果里已带 content 字段则可直接用，无需再抓）。"
    )
    public Map<String, Object> search(
            @AiToolParam(name = "query", description = "搜索关键字")
            String query
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(query)) {
            result.put("error", "empty_query");
            return result;
        }

        List<WebSearchProvider> ordered = providers.stream()
                .filter(WebSearchProvider::isAvailable)
                .sorted(Comparator.comparingInt(WebSearchProvider::priority))
                .toList();
        if (ordered.isEmpty()) {
            result.put("error", "no_provider");
            return result;
        }

        String lastBackend = null;
        for (WebSearchProvider provider : ordered) {
            lastBackend = provider.name();
            try {
                List<WebSearchResult> items = provider.search(query, MAX_RESULTS);
                if (items != null && !items.isEmpty()) {
                    result.put("backend", provider.name());
                    result.put("query", query);
                    result.put("count", items.size());
                    result.put("results", toMaps(items));
                    return result;
                }
                log.info("web_search backend={} 返回空，降级下一个", provider.name());
            } catch (Exception e) {
                log.warn("web_search backend={} 失败，降级下一个 query={}", provider.name(), query, e);
            }
        }

        // 所有后端都空 / 失败
        result.put("backend", lastBackend);
        result.put("query", query);
        result.put("count", 0);
        result.put("results", new ArrayList<>());
        result.put("note", "all_backends_empty");
        return result;
    }

    private List<Map<String, Object>> toMaps(List<WebSearchResult> items) {
        List<Map<String, Object>> out = new ArrayList<>(items.size());
        for (WebSearchResult r : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", r.title());
            m.put("url", r.url());
            m.put("snippet", r.snippet());
            if (StringUtils.isNotBlank(r.content())) {
                m.put("content", r.content());
            }
            out.add(m);
        }
        return out;
    }
}
