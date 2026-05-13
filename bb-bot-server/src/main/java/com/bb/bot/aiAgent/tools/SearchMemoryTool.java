package com.bb.bot.aiAgent.tools;

import com.alibaba.fastjson2.JSON;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.memory.FactStore;
import com.bb.bot.database.aiAgent.entity.AiMemoryFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 openhanako 的 search_memory（lib/memory/memory-search.js）。
 *
 * <p>Tag 优先 + FTS 兜底，命中按时间倒序。caller user_id namespace 隔离，
 * 不能跨用户查（zero cross-user leak）。</p>
 */
@Slf4j
@Component
public class SearchMemoryTool {

    @Autowired
    private FactStore factStore;

    @AiTool(
            name = "search_memory",
            description = "在你的长期记忆里搜索事实。query 是关键字（中文 / 英文 / 短语都行），" +
                    "tags 是可选的标签数组（精确匹配，命中越多越靠前）。" +
                    "返回最多 limit 条事实，按相关度+时间排序。" +
                    "当用户问「我之前说过什么」「记得不」「我喜欢啥」时调本工具检索过往记忆。"
    )
    public Map<String, Object> search(
            @AiToolParam(name = "query", description = "搜索关键字，可空（仅按 tags 搜）", required = false)
            String query,
            @AiToolParam(name = "tags", description = "可选标签数组，例如 [\"splatoon\",\"武器\"]", required = false)
            List<String> tags,
            @AiToolParam(name = "limit", description = "结果上限（默认 10，最大 30）", required = false)
            Integer limit
    ) {
        int cap = limit == null || limit <= 0 ? 10 : Math.min(limit, 30);
        String userId = currentUserId();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            List<AiMemoryFact> hits = factStore.search(userId, query, tags, cap);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiMemoryFact f : hits) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", f.getId());
                item.put("fact", f.getFact());
                item.put("tags", parseTags(f.getTags()));
                item.put("factTime", f.getFactTime());
                item.put("createdAt", f.getCreatedAt());
                items.add(item);
            }
            out.put("query", query);
            out.put("tags", tags == null ? Collections.emptyList() : tags);
            out.put("count", items.size());
            out.put("hits", items);
            return out;
        } catch (Exception e) {
            log.warn("search_memory 失败 user={}", userId, e);
            out.put("error", "search_failed");
            out.put("message", e.getMessage());
            return out;
        }
    }

    private List<String> parseTags(String json) {
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    /** AiToolExecutor 调本方法时把 caller user 传到 ThreadLocal；为简化先复用 splatoon 等其它
     *  无 user 工具的设计：从一个静态 holder 取。后续 M8.6 系统集成时统一打通。 */
    private String currentUserId() {
        return MemoryToolContext.getUserId();
    }
}
