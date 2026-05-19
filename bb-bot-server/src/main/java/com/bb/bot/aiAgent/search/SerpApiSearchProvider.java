package com.bb.bot.aiAgent.search;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SerpAPI 后端（Google 结果，付费）。需要 {@code aiAgent.webSearch.serpApiKey}。
 */
@Slf4j
@Component
public class SerpApiSearchProvider implements WebSearchProvider {

    @Autowired
    private RestUtils restUtils;

    @Value("${aiAgent.webSearch.serpApiKey:}")
    private String serpApiKey;

    @Override
    public String name() {
        return "serpapi";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.isNotBlank(serpApiKey);
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        String url = "https://serpapi.com/search.json?engine=google&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&api_key=" + serpApiKey;
        String body = restUtils.get(url, String.class);

        List<WebSearchResult> items = new ArrayList<>();
        JSONObject root = JSON.parseObject(body);
        JSONArray organic = root == null ? null : root.getJSONArray("organic_results");
        if (organic == null) {
            return items;
        }
        for (int i = 0; i < organic.size() && items.size() < maxResults; i++) {
            JSONObject o = organic.getJSONObject(i);
            items.add(WebSearchResult.of(o.getString("title"), o.getString("link"), o.getString("snippet")));
        }
        return items;
    }
}
