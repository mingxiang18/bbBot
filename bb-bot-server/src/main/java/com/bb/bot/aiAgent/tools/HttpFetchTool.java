package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 / 实用工具：抓取一个 HTTP(S) URL，返回正文文本和 og:* 元数据。
 *
 * <p>安全护栏（MVP）：</p>
 * <ul>
 *   <li>scheme 仅允许 http / https</li>
 *   <li>解析后 IP 落在私有段（10/172.16/192.168/127）则拒绝（SSRF 防护）</li>
 *   <li>正文截断 4KB，避免把超大网页塞回 LLM 浪费 token</li>
 * </ul>
 */
@Slf4j
@Component
public class HttpFetchTool {

    /** 提到 32KB：足够装下 splatoon3.ink/data/schedules.json 这类完整 API 响应。
     *  LLM 上下文窗口都 >=128K，32KB 不会撑爆。 */
    private static final int BODY_LIMIT = 32 * 1024;

    @AiTool(
            name = "http_fetch",
            description = "抓取一个公网 URL 的正文文本和 og:title / og:description 元数据。" +
                    "适合用户让你「读一下 / 抓一下 / 总结一下某个网页」的场景。" +
                    "仅支持 http / https，禁止访问内网。"
    )
    public Map<String, Object> fetch(
            @AiToolParam(name = "url", description = "要抓取的 http 或 https URL")
            String url
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                result.put("error", "unsupported_scheme");
                result.put("scheme", scheme);
                return result;
            }
            if (isInternalHost(uri.getHost())) {
                result.put("error", "internal_host_blocked");
                result.put("host", uri.getHost());
                return result;
            }
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 bbBot-agent")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();
            result.put("url", url);
            result.put("title", doc.title());
            result.put("ogTitle", metaContent(doc, "og:title"));
            result.put("ogDescription", metaContent(doc, "og:description"));
            String body = doc.body() == null ? "" : doc.body().text();
            if (body.length() > BODY_LIMIT) {
                body = body.substring(0, BODY_LIMIT) + "...[truncated]";
            }
            result.put("body", body);
            return result;
        } catch (Exception e) {
            log.warn("http_fetch 失败 url={}", url, e);
            result.put("error", "fetch_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    private boolean isInternalHost(String host) {
        if (host == null || host.isEmpty()) return true;
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return true;
        }
    }

    private String metaContent(Document doc, String property) {
        org.jsoup.select.Elements els = doc.select("meta[property=" + property + "]");
        if (els.isEmpty()) return null;
        return els.first().attr("content");
    }
}
