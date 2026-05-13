package com.bb.bot.aiAgent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用原语：搜索引擎查询。
 *
 * <p>后端选择（优先级）：</p>
 * <ol>
 *   <li>如果 {@code aiAgent.webSearch.serpApiKey} 已配置 → 走 SerpAPI（更稳，但需付费）</li>
 *   <li>否则 → 走 DuckDuckGo HTML 端点（免 key，但稳定性看 DDG 心情）</li>
 * </ol>
 *
 * <p>返回最多 5 条结果：{@code title / url / snippet}。</p>
 */
@Slf4j
@Component
public class WebSearchTool {

    private static final int MAX_RESULTS = 5;

    @Value("${aiAgent.webSearch.serpApiKey:}")
    private String serpApiKey;

    @Value("${aiAgent.webSearch.duckDuckGoUrl:https://duckduckgo.com/html/}")
    private String duckDuckGoUrl;

    @AiTool(
            name = "web_search",
            description = "用搜索引擎搜公网信息。用户问「最新 / 现在 / 实时」的事，" +
                    "或你不知道某个名词时调本工具。返回最多 5 条结果（title / url / snippet）。" +
                    "拿到 url 后通常再用 http_fetch 把感兴趣的页面正文抓回来读。"
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
        try {
            List<Map<String, Object>> items;
            if (StringUtils.isNotBlank(serpApiKey)) {
                items = searchViaSerpApi(query);
                result.put("backend", "serpapi");
            } else {
                items = searchViaDuckDuckGo(query);
                result.put("backend", "duckduckgo");
            }
            result.put("query", query);
            result.put("count", items.size());
            result.put("results", items);
            return result;
        } catch (Exception e) {
            log.warn("web_search 失败 query={}", query, e);
            result.put("error", "search_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    /** DuckDuckGo 的 /html/ 端点返回 SSR 的 HTML，结果以 .result 选择器列出。 */
    private List<Map<String, Object>> searchViaDuckDuckGo(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = duckDuckGoUrl + "?q=" + encoded;
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 bbBot-agent")
                .timeout(15000)
                .followRedirects(true)
                .get();
        List<Map<String, Object>> items = new ArrayList<>();
        Elements results = doc.select(".result");
        for (Element r : results) {
            if (items.size() >= MAX_RESULTS) break;
            Elements aEls = r.select(".result__a");
            Elements snipEls = r.select(".result__snippet");
            Element titleEl = aEls.isEmpty() ? null : aEls.first();
            Element snippetEl = snipEls.isEmpty() ? null : snipEls.first();
            if (titleEl == null) continue;
            String title = titleEl.text();
            String href = titleEl.attr("href");
            // DDG 把真实 URL 放在 uddg 参数里，回收一下
            href = decodeDdgRedirect(href);
            String snippet = snippetEl == null ? "" : snippetEl.text();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("url", href);
            item.put("snippet", snippet);
            items.add(item);
        }
        return items;
    }

    private String decodeDdgRedirect(String href) {
        try {
            int idx = href.indexOf("uddg=");
            if (idx < 0) return href;
            String enc = href.substring(idx + 5);
            int amp = enc.indexOf('&');
            if (amp > 0) enc = enc.substring(0, amp);
            return java.net.URLDecoder.decode(enc, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return href;
        }
    }

    private List<Map<String, Object>> searchViaSerpApi(String query) throws Exception {
        String url = "https://serpapi.com/search.json?engine=google&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&api_key=" + serpApiKey;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("serpapi HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JSONObject root = JSON.parseObject(resp.body());
        JSONArray organic = root.getJSONArray("organic_results");
        List<Map<String, Object>> items = new ArrayList<>();
        if (organic == null) return items;
        for (int i = 0; i < organic.size() && items.size() < MAX_RESULTS; i++) {
            JSONObject o = organic.getJSONObject(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", o.getString("title"));
            item.put("url", o.getString("link"));
            item.put("snippet", o.getString("snippet"));
            items.add(item);
        }
        return items;
    }
}
