package com.bb.bot.handler.news.fetch;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.NewsFetcher;
import com.bb.bot.handler.news.contract.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 资讯采集器实现（任务 T2）。
 *
 * <p>遍历 {@link NewsConfig#getSources()}，按 via（direct / rsshub）确定 URL，
 * 带浏览器 User-Agent 发 GET 拿 RSS XML，交给 {@link RssParser} 解析为 {@link NewsItem}。
 * 单源失败隔离：每源拉取+解析包 try-catch，失败记 error 日志并跳过，绝不中断 fetchAll。</p>
 */
@Slf4j
@Component
public class NewsFetcherImpl implements NewsFetcher {

    /** 浏览器 User-Agent，避免部分源对默认 UA 返回 403。 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @Autowired
    private RestClient restClient;

    @Autowired
    private NewsConfig newsConfig;

    @Override
    public List<NewsItem> fetchAll() {
        List<NewsItem> all = new ArrayList<>();

        List<NewsConfig.Source> sources = newsConfig.getSources();
        if (sources == null || sources.isEmpty()) {
            log.warn("[news] 未配置任何资讯源，fetchAll 返回空");
            return all;
        }

        int limit = newsConfig.getPerSourceLimit();
        for (NewsConfig.Source source : sources) {
            try {
                String url = resolveUrl(source);
                if (url == null || url.isBlank()) {
                    log.error("[news] 源[{}]无法确定 URL（via={}），跳过", source.getName(), source.getVia());
                    continue;
                }

                String xml = restClient.get()
                        .uri(url)
                        .header("User-Agent", USER_AGENT)
                        .retrieve()
                        .body(String.class);

                List<NewsItem> items = RssParser.parse(
                        xml, source.getName(), source.getCategory(), source.getLang());

                if (limit > 0 && items.size() > limit) {
                    items = items.subList(0, limit);
                }

                all.addAll(items);
                log.info("[news] 源[{}]采集 {} 条（url={}）", source.getName(), items.size(), url);
            } catch (Exception e) {
                log.error("[news] 源[{}]采集失败，已跳过（via={}）", source.getName(), source.getVia(), e);
            }
        }

        log.info("[news] fetchAll 完成，共 {} 条（源数 {}）", all.size(), sources.size());
        return all;
    }

    /**
     * 根据 via 解析最终请求 URL：
     * <ul>
     *     <li>direct：直接用 {@code source.url}</li>
     *     <li>rsshub：{@code rsshub.baseUrl + source.path}</li>
     * </ul>
     */
    private String resolveUrl(NewsConfig.Source source) {
        String via = source.getVia();
        if ("rsshub".equalsIgnoreCase(via)) {
            String base = newsConfig.getRsshub() == null ? null : newsConfig.getRsshub().getBaseUrl();
            String path = source.getPath();
            if (base == null || path == null) {
                return null;
            }
            return base + path;
        }
        // 默认按 direct 处理
        return source.getUrl();
    }
}
