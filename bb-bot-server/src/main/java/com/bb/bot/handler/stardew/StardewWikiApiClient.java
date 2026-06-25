package com.bb.bot.handler.stardew;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class StardewWikiApiClient implements StardewWikiClient {

    private static final int EXCERPT_LIMIT = 1200;

    private final RestUtils restUtils;

    @Value("${stardew.wiki.apiUrl:https://zh.stardewvalleywiki.com/mediawiki/api.php}")
    private String apiUrl;

    @Value("${stardew.wiki.pageBaseUrl:https://zh.stardewvalleywiki.com/}")
    private String pageBaseUrl;

    public StardewWikiApiClient(RestUtils restUtils) {
        this.restUtils = restUtils;
    }

    @Override
    public List<StardewWikiPage> search(String query, int maxResults) {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        try {
            List<SearchHit> hits = searchAllCandidates(query, Math.max(maxResults, 3));
            List<StardewWikiPage> pages = new ArrayList<>();
            Set<String> fetchedTitles = new LinkedHashSet<>();
            for (SearchHit hit : hits) {
                if (pages.size() >= maxResults) {
                    break;
                }
                if (StringUtils.isBlank(hit.title()) || !fetchedTitles.add(hit.title())) {
                    continue;
                }
                StardewWikiPage page = fetchPage(hit.title());
                if (page != null && StringUtils.isNotBlank(page.getExcerpt())) {
                    pages.add(page);
                }
            }
            return pages;
        } catch (Exception e) {
            log.warn("Stardew Wiki search failed query={}", query, e);
            return List.of();
        }
    }

    private List<SearchHit> searchAllCandidates(String query, int maxResults) {
        List<String> candidates = buildSearchQueries(query);
        List<SearchHit> hits = new ArrayList<>();
        Set<String> seenTitles = new LinkedHashSet<>();
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            String candidate = candidates.get(candidateIndex);
            JSONArray searchResults = searchTitles(candidate, maxResults);
            if (searchResults == null) {
                continue;
            }
            for (int hitIndex = 0; hitIndex < searchResults.size(); hitIndex++) {
                JSONObject hit = searchResults.getJSONObject(hitIndex);
                String title = hit.getString("title");
                if (StringUtils.isBlank(title) || !seenTitles.add(title)) {
                    continue;
                }
                hits.add(new SearchHit(title, candidate, scoreHit(query, candidate, title), candidateIndex, hitIndex));
            }
        }
        return hits.stream()
                .sorted(Comparator
                        .comparingInt(SearchHit::score).reversed()
                        .thenComparingInt(SearchHit::candidateIndex)
                        .thenComparingInt(SearchHit::hitIndex))
                .toList();
    }

    private int scoreHit(String query, String candidate, String title) {
        String q = normalize(query);
        String c = normalize(candidate);
        String t = normalize(title);
        int score = 0;
        if (!t.isEmpty() && q.contains(t)) {
            score += 100;
        }
        if (!c.isEmpty() && t.equals(c)) {
            score += 80;
        } else if (!c.isEmpty() && (t.contains(c) || c.contains(t))) {
            score += 40;
        }
        if (!t.isEmpty() && (q.startsWith(t) || q.endsWith(t))) {
            score += 10;
        }
        return score;
    }

    private JSONArray searchTitles(String query, int maxResults) {
        String url = apiUrl
                + "?action=query&list=search&format=json&utf8=1&srlimit=" + maxResults
                + "&srsearch=" + enc(query);
        String body = restUtils.get(url, headers(), String.class);
        JSONObject json = JSON.parseObject(body);
        JSONObject queryNode = json.getJSONObject("query");
        return queryNode == null ? new JSONArray() : queryNode.getJSONArray("search");
    }

    private List<String> buildSearchQueries(String query) {
        Set<String> queries = new LinkedHashSet<>();

        String[][] hints = {
                {"战斗", "战斗"},
                {"采矿", "采矿"},
                {"耕种", "耕种"},
                {"农业", "耕种"},
                {"钓鱼", "钓鱼"},
                {"觅食", "觅食"},
                {"采集", "觅食"},
                {"运气", "运气"},
                {"烹饪", "烹饪"},
                {"献祭", "收集包"},
                {"收集包", "收集包"},
                {"沙漠", "沙漠"},
                {"姜岛", "姜岛"},
                {"温室", "温室"},
                {"火山", "火山地牢"},
                {"头骨", "骷髅洞穴"},
                {"骷髅", "骷髅洞穴"},
                {"博物馆", "博物馆"},
                {"鱼塘", "鱼塘"},
                {"送礼", "友谊"},
                {"礼物", "友谊"},
                {"好感", "友谊"},
                {"烹饪", "烹饪"},
                {"料理", "烹饪"},
                {"制作", "打造"},
                {"合成", "打造"},
                {"精通", "精通"},
                {"金核桃", "金核桃"},
                {"银河剑", "银河之剑"},
                {"银河之剑", "银河之剑"},
                {"洒水器", "洒水器"},
                {"树液采集器", "树液采集器"},
                {"晶球", "晶球"},
                {"矮人卷轴", "矮人卷轴"},
                {"恐龙蛋", "恐龙蛋"},
                {"秘密纸条", "秘密纸条"}
        };
        for (String[] hint : hints) {
            if (query.contains(hint[0])) {
                addIfPresent(queries, hint[1]);
            }
        }

        String simplified = simplifyQuery(query);
        addIfPresent(queries, simplified);
        addIfPresent(queries, query);
        return new ArrayList<>(queries);
    }

    private String simplifyQuery(String query) {
        return query
                .replace("星露谷物语", "")
                .replace("星露谷", "")
                .replace("如何", "")
                .replace("怎么", "")
                .replace("怎样", "")
                .replace("在哪", "")
                .replace("哪里", "")
                .replace("如何获得", "")
                .replace("怎么获得", "")
                .replace("怎么获取", "")
                .replace("怎么拿", "")
                .replace("怎么做", "")
                .replace("是什么", "")
                .replace("快速", "")
                .replace("升级", "")
                .replace("提升", "")
                .replace("攻略", "")
                .replace("礼物", "")
                .replace("技能", "")
                .replace("等级", "")
                .replace("当前", "")
                .replace("现在", "")
                .replace("需要", "")
                .replace("什么", "")
                .replace("获得", "")
                .replace("获取", "")
                .replace("收集", "")
                .replace("解锁", "")
                .replace("入手", "")
                .replace("拿", "")
                .replace("做", "")
                .replace("刷", "")
                .replace("用", "")
                .replace("吗", "")
                .replace("？", "")
                .replace("?", "")
                .trim();
    }

    private void addIfPresent(Set<String> queries, String query) {
        if (StringUtils.isNotBlank(query)) {
            queries.add(query.trim());
        }
    }

    private StardewWikiPage fetchPage(String title) {
        String url = apiUrl
                + "?action=parse&format=json&redirects=1&prop=text%7Cdisplaytitle&page=" + enc(title);
        String body = restUtils.get(url, headers(), String.class);
        JSONObject json = JSON.parseObject(body);
        JSONObject parse = json.getJSONObject("parse");
        if (parse == null) {
            return null;
        }
        String displayTitle = StringUtils.defaultIfBlank(parse.getString("displaytitle"), title);
        JSONObject text = parse.getJSONObject("text");
        String html = text == null ? "" : text.getString("*");
        String excerpt = extractUsefulText(html);
        return StardewWikiPage.builder()
                .title(Jsoup.parse(displayTitle).text())
                .url(pageBaseUrl + encPath(title))
                .excerpt(excerpt)
                .build();
    }

    private String extractUsefulText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script,style,.navbox,.metadata,.mw-editsection,.toc,.infobox").remove();
        StringBuilder out = new StringBuilder();
        for (Element el : doc.select("p, li")) {
            String text = el.text().trim();
            if (text.length() < 6 || isBoilerplate(text)) {
                continue;
            }
            if (out.length() + text.length() + 2 > EXCERPT_LIMIT) {
                break;
            }
            out.append(text).append("\n");
        }
        for (Element row : doc.select("table.wikitable tr, table.sortable tr")) {
            String text = row.text().trim();
            if (text.length() < 8 || isBoilerplate(text) || out.toString().contains(text)) {
                continue;
            }
            if (out.length() + text.length() + 2 > EXCERPT_LIMIT) {
                break;
            }
            out.append(text).append("\n");
        }
        return out.toString().trim();
    }

    private boolean isBoilerplate(String text) {
        return text.startsWith("导航")
                || text.startsWith("跳转")
                || text.contains("此页面最后编辑于")
                || text.contains("本站内容使用");
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; bbBot-stardew-guide)");
        return headers;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encPath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "_");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replace(" ", "")
                .replace("_", "")
                .replace("（", "(")
                .replace("）", ")")
                .trim();
    }

    private record SearchHit(String title, String candidate, int score, int candidateIndex, int hitIndex) {
    }
}
