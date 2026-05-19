package com.bb.bot.aiAgent.search;

import com.bb.bot.common.util.RestUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DuckDuckGo HTML 端点后端（免 key 的兜底）。
 *
 * <p>优先级最低：DDG 的 {@code /html/} 端点对程序化访问已不稳定（常返回验证码 / 空页），
 * 仅作为「一个 key 都没配」时的最后退路。生产建议配 Brave 或 Tavily。</p>
 */
@Slf4j
@Component
public class DuckDuckGoSearchProvider implements WebSearchProvider {

    @Autowired
    private RestUtils restUtils;

    @Value("${aiAgent.webSearch.duckDuckGoUrl:https://duckduckgo.com/html/}")
    private String duckDuckGoUrl;

    @Override
    public String name() {
        return "duckduckgo";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        String url = duckDuckGoUrl + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (compatible; bbBot-agent)");
        String html = restUtils.get(url, headers, String.class);

        Document doc = Jsoup.parse(html, url);
        List<WebSearchResult> items = new ArrayList<>();
        for (Element r : doc.select(".result")) {
            if (items.size() >= maxResults) {
                break;
            }
            Elements aEls = r.select(".result__a");
            if (aEls.isEmpty()) {
                continue;
            }
            Element titleEl = aEls.first();
            Elements snipEls = r.select(".result__snippet");
            String snippet = snipEls.isEmpty() ? "" : snipEls.first().text();
            // DDG 把真实 URL 塞在 uddg 参数里，回收一下
            String href = decodeDdgRedirect(titleEl.attr("href"));
            items.add(WebSearchResult.of(titleEl.text(), href, snippet));
        }
        return items;
    }

    private String decodeDdgRedirect(String href) {
        try {
            int idx = href.indexOf("uddg=");
            if (idx < 0) {
                return href;
            }
            String enc = href.substring(idx + 5);
            int amp = enc.indexOf('&');
            if (amp > 0) {
                enc = enc.substring(0, amp);
            }
            return URLDecoder.decode(enc, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return href;
        }
    }
}
