package com.bb.bot.aiAgent.search;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily 搜索后端（专为 agent 设计）。
 *
 * <p>结果自带正文摘要（{@code content}），模型常常不必再 http_fetch 逐个抓页。
 * 免费额度 1000 次/月。需要 {@code aiAgent.webSearch.tavilyApiKey}。</p>
 */
@Slf4j
@Component
public class TavilySearchProvider implements WebSearchProvider {

    /** content 截断长度：避免把超长正文整段塞回 LLM。 */
    private static final int SNIPPET_LIMIT = 320;

    @Autowired
    private RestUtils restUtils;

    @Value("${aiAgent.webSearch.tavilyApiKey:}")
    private String apiKey;

    @Value("${aiAgent.webSearch.tavilyUrl:https://api.tavily.com/search}")
    private String tavilyUrl;

    @Override
    public String name() {
        return "tavily";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.isNotBlank(apiKey);
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("api_key", apiKey);
        reqBody.put("query", query);
        reqBody.put("max_results", maxResults);
        reqBody.put("search_depth", "basic");
        // restUtils.post(url, params, clazz)：自动 application/json + 解析响应
        JSONObject root = restUtils.post(tavilyUrl, reqBody, JSONObject.class);

        List<WebSearchResult> items = new ArrayList<>();
        if (root == null) {
            return items;
        }
        JSONArray results = root.getJSONArray("results");
        if (results == null) {
            return items;
        }
        for (int i = 0; i < results.size() && items.size() < maxResults; i++) {
            JSONObject o = results.getJSONObject(i);
            String content = o.getString("content");
            String snippet = content == null ? ""
                    : (content.length() > SNIPPET_LIMIT ? content.substring(0, SNIPPET_LIMIT) + "…" : content);
            items.add(new WebSearchResult(o.getString("title"), o.getString("url"), snippet, content));
        }
        return items;
    }
}
