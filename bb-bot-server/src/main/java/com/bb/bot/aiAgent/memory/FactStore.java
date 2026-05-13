package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryFact;
import com.bb.bot.database.aiAgent.mapper.AiMemoryFactMapper;
import com.bb.bot.database.aiAgent.service.IAiMemoryFactService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对应 openhanako 的 FactStore（lib/memory/fact-store.js）。
 *
 * <p>提供：</p>
 * <ul>
 *   <li>add / addBatch —— 写入事实，自动归一化生成 search_text</li>
 *   <li>searchByTags —— tag 命中（JSON 数组里精确匹配）</li>
 *   <li>searchFullText —— FULLTEXT MATCH ngram；失败 fallback LIKE</li>
 * </ul>
 *
 * <p>v2 故意没有 importance / decay 字段，靠时间窗 + tag 命中数排序代替。</p>
 */
@Slf4j
@Component
public class FactStore {

    @Autowired
    private IAiMemoryFactService factService;

    @Autowired
    private AiMemoryFactMapper factMapper;

    @Value("${aiAgent.memory.factMaxBytes:64000}")
    private int factMaxBytes;

    /** 单条事实入库；fact 文本太长截断；自动生成 search_text。 */
    public AiMemoryFact add(String userId, String fact, Collection<String> tags,
                             LocalDateTime factTime, String sourceSessionId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(fact)) return null;
        String trimmed = fact.length() > factMaxBytes ? fact.substring(0, factMaxBytes) : fact;
        AiMemoryFact row = new AiMemoryFact();
        row.setUserId(userId);
        row.setFact(trimmed);
        row.setSearchText(normalize(trimmed));
        row.setTags(JSON.toJSONString(tags == null ? Collections.emptyList() : tags));
        row.setFactTime(factTime);
        row.setSourceSessionId(sourceSessionId);
        row.setCreatedAt(LocalDateTime.now());
        factService.save(row);
        return row;
    }

    public int addBatch(String userId, List<Map<String, Object>> facts) {
        int n = 0;
        for (Map<String, Object> f : facts) {
            String fact = (String) f.get("fact");
            @SuppressWarnings("unchecked")
            Collection<String> tags = (Collection<String>) f.getOrDefault("tags", Collections.emptyList());
            if (add(userId, fact, tags, null, (String) f.get("sourceSessionId")) != null) {
                n++;
            }
        }
        return n;
    }

    /** Tag 精确匹配（OR 逻辑），按命中数量降序。 */
    public List<AiMemoryFact> searchByTags(String userId, Collection<String> queryTags, int limit) {
        if (StringUtils.isBlank(userId) || queryTags == null || queryTags.isEmpty()) return Collections.emptyList();
        // 简化：MySQL 没有 SQLite json_each。用 LIKE 候选 + 应用层精确比对
        List<AiMemoryFact> candidates = factService.list(new LambdaQueryWrapper<AiMemoryFact>()
                .eq(AiMemoryFact::getUserId, userId)
                .and(w -> {
                    for (String t : queryTags) {
                        w.like(AiMemoryFact::getTags, "\"" + t + "\"").or();
                    }
                })
                .orderByDesc(AiMemoryFact::getCreatedAt)
                .last("limit " + (limit * 5)));
        // 按命中数排序
        Map<AiMemoryFact, Integer> hitCount = new LinkedHashMap<>();
        for (AiMemoryFact f : candidates) {
            Set<String> ftags = parseTags(f.getTags());
            int hits = 0;
            for (String q : queryTags) {
                if (ftags.contains(q)) hits++;
            }
            if (hits > 0) hitCount.put(f, hits);
        }
        return hitCount.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** FULLTEXT 搜索；如果索引不可用（旧 MySQL）fallback 到 LIKE。 */
    public List<AiMemoryFact> searchFullText(String userId, String query, int limit) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(query)) return Collections.emptyList();
        String norm = normalize(query);
        try {
            List<AiMemoryFact> rows = factMapper.fulltextSearch(userId, norm, limit);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            log.debug("fulltext 搜索失败，fallback to LIKE", e);
        }
        // fallback: LIKE 模糊
        return factService.list(new LambdaQueryWrapper<AiMemoryFact>()
                .eq(AiMemoryFact::getUserId, userId)
                .like(AiMemoryFact::getSearchText, norm)
                .orderByDesc(AiMemoryFact::getCreatedAt)
                .last("limit " + limit));
    }

    /** tag + 全文复合：tag 命中优先，再补 FTS 凑数。 */
    public List<AiMemoryFact> search(String userId, String query, Collection<String> tags, int limit) {
        List<AiMemoryFact> tagHits = (tags == null || tags.isEmpty())
                ? Collections.emptyList()
                : searchByTags(userId, tags, limit);
        if (tagHits.size() >= limit) return tagHits.subList(0, limit);
        Set<Long> seen = new LinkedHashSet<>();
        List<AiMemoryFact> out = new ArrayList<>(tagHits);
        for (AiMemoryFact f : tagHits) seen.add(f.getId());
        if (StringUtils.isNotBlank(query)) {
            for (AiMemoryFact f : searchFullText(userId, query, limit)) {
                if (seen.add(f.getId())) {
                    out.add(f);
                    if (out.size() >= limit) break;
                }
            }
        }
        return out;
    }

    public List<AiMemoryFact> recentForUser(String userId, int limit) {
        return factService.list(new LambdaQueryWrapper<AiMemoryFact>()
                .eq(AiMemoryFact::getUserId, userId)
                .orderByDesc(AiMemoryFact::getCreatedAt)
                .last("limit " + limit));
    }

    private Set<String> parseTags(String json) {
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return new LinkedHashSet<>(list == null ? Collections.emptyList() : list);
        } catch (Exception ignore) {
            return Collections.emptySet();
        }
    }

    /**
     * 归一化：转小写 + 去标点 + 中文按字切空格。ngram FULLTEXT 自己也会分词，
     * 这里主要为 fallback LIKE 模式提供 friendly text。
     */
    static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(' ');
            }
            // 中文字符前后加空格（n-gram tokenizer 会自己处理，这里友好显式）
            if (c >= 0x4E00 && c <= 0x9FFF) {
                if (sb.length() > 1 && sb.charAt(sb.length() - 2) != ' ') {
                    sb.insert(sb.length() - 1, ' ');
                }
                sb.append(' ');
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }
}
