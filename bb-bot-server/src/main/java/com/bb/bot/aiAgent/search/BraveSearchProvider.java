package com.bb.bot.aiAgent.search;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Brave Search API 后端（默认主后端）。
 *
 * <p>独立索引、质量稳定，免费额度 2000 次/月。需要 {@code aiAgent.webSearch.braveApiKey}。</p>
 */
@Slf4j
@Component
public class BraveSearchProvider implements WebSearchProvider {

    /** 走项目统一的 RestClient（带代理），否则国内调不到外网 API。 */
    @Autowired
    private RestUtils restUtils;

    @Value("${aiAgent.webSearch.braveApiKey:}")
    private String apiKey;

    @Value("${aiAgent.webSearch.braveUrl:https://api.search.brave.com/res/v1/web/search}")
    private String braveUrl;

    @Override
    public String name() {
        return "brave";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.isNotBlank(apiKey);
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        String url = braveUrl + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + maxResults;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("X-Subscription-Token", apiKey);
        String body = restUtils.get(url, headers, String.class);

        List<WebSearchResult> items = new ArrayList<>();
        JSONObject root = JSON.parseObject(body);
        if (root == null) {
            return items;
        }
        JSONObject web = root.getJSONObject("web");
        JSONArray results = web == null ? null : web.getJSONArray("results");
        if (results == null) {
            return items;
        }
        for (int i = 0; i < results.size() && items.size() < maxResults; i++) {
            JSONObject o = results.getJSONObject(i);
            // description 里带 <strong> 高亮标签，用 Jsoup 抽纯文本
            String desc = o.getString("description");
            String snippet = StringUtils.isBlank(desc) ? "" : Jsoup.parse(desc).text();
            items.add(WebSearchResult.of(o.getString("title"), o.getString("url"), snippet));
        }
        return items;
    }
}
