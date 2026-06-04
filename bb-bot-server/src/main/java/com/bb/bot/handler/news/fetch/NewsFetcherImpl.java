package com.bb.bot.handler.news.fetch;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.config.RestClientConfig;
import com.bb.bot.handler.news.contract.NewsFetcher;
import com.bb.bot.handler.news.contract.NewsItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** XML 声明里的 encoding 探测：{@code <?xml ... encoding="UTF-8"?>}。 */
    private static final Pattern XML_ENCODING =
            Pattern.compile("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Autowired
    private NewsConfig newsConfig;

    @Value("${rest.proxyIp:}")
    private String proxyIp;

    @Value("${rest.proxyPort:}")
    private Integer proxyPort;

    /**
     * news 专用 HTTP client（Phase 4）：带真实 connect + response(socket) 读超时，避免单源卡死拖垮整轮。
     * 与全局 restClient 分离——全局 client 被 LLM 流式复用，需长超时，不能套用 news 的短超时。
     * 懒初始化，便于无 Spring 的活体 IT 直接复用。
     */
    private volatile RestClient newsHttpClient;

    @Override
    public List<NewsItem> fetchAll() {
        List<NewsConfig.Source> sources = newsConfig.getSources();
        if (sources == null || sources.isEmpty()) {
            log.warn("[news] 未配置任何资讯源，fetchAll 返回空");
            return new ArrayList<>();
        }

        int limit = newsConfig.getPerSourceLimit();
        int concurrency = Math.max(1, newsConfig.getFetch().getConcurrency());
        int poolSize = Math.min(concurrency, sources.size());

        // 有限并发抓取：单源失败隔离（fetchOne 内部 try-catch，返回空列表，不抛）
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        List<NewsItem> all = new ArrayList<>();
        int ok = 0;
        int failed = 0;
        try {
            List<Future<List<NewsItem>>> futures = new ArrayList<>();
            for (NewsConfig.Source source : sources) {
                final NewsConfig.Source s = source;
                Callable<List<NewsItem>> task = () -> fetchOne(s, limit);
                futures.add(pool.submit(task));
            }
            for (Future<List<NewsItem>> f : futures) {
                try {
                    List<NewsItem> items = f.get();
                    if (items.isEmpty()) {
                        failed++;
                    } else {
                        ok++;
                        all.addAll(items);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failed++;
                } catch (ExecutionException ee) {
                    failed++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        log.info("[news] fetchAll 完成，共 {} 条（源数 {}，成功 {}，失败/空 {}，并发 {}）",
                all.size(), sources.size(), ok, failed, poolSize);
        return all;
    }

    /** 抓取单源并解析；任何异常都被吞掉返回空列表（单源失败隔离）。 */
    private List<NewsItem> fetchOne(NewsConfig.Source source, int limit) {
        long start = System.currentTimeMillis();
        try {
            String url = resolveUrl(source);
            if (url == null || url.isBlank()) {
                log.error("[news] 源[{}]无法确定 URL（via={}），跳过", source.getName(), source.getVia());
                return new ArrayList<>();
            }

            // 取字节流后按 XML 声明的 encoding 解码：源站未声明 charset 时 body(String) 默认
            // ISO-8859-1 会导致中文乱码，故取 byte[] 自行探测。
            byte[] bytes = client().get()
                    .uri(url)
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(byte[].class);
            String xml = decodeXml(bytes);

            List<NewsItem> items = RssParser.parse(
                    xml, source.getName(), source.getCategory(), source.getLang());

            if (limit > 0 && items.size() > limit) {
                items = items.subList(0, limit);
            }
            long cost = System.currentTimeMillis() - start;
            if (items.isEmpty()) {
                log.warn("[news] 源[{}]采集 0 条（疑似源停更/解析空，cost={}ms url={}）", source.getName(), cost, url);
            } else {
                log.info("[news] 源[{}]采集 {} 条（cost={}ms url={}）", source.getName(), items.size(), cost, url);
            }
            return items;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            // 分类错误便于排查"源是否失效"：timeout（读/连超时）/ connect（连不上）/ http（4xx/5xx）/ parse（解析失败）
            log.error("[news] 源[{}]采集失败，已跳过（via={} errorType={} cost={}ms）：{}",
                    source.getName(), source.getVia(), classifyError(e), cost, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 把抓取异常归类为简短可读的错误类型，供日志与（P2）源健康记录使用。
     * 沿 cause 链查找已知异常，命中即返回对应标签，否则 error。
     */
    static String classifyError(Throwable e) {
        Throwable t = e;
        // 沿 cause 链最多走 8 层，防御性避免循环引用导致死循环
        for (int depth = 0; t != null && depth < 8; t = t.getCause(), depth++) {
            String cn = t.getClass().getName();
            if (t instanceof java.net.SocketTimeoutException
                    || cn.contains("Timeout") || cn.contains("TimeoutException")) {
                return "timeout";
            }
            if (t instanceof java.net.ConnectException
                    || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.NoRouteToHostException) {
                return "connect";
            }
            if (cn.contains("HttpClientErrorException") || cn.contains("HttpServerErrorException")
                    || cn.contains("HttpStatusCodeException")) {
                return "http";
            }
            if (cn.contains("SAX") || cn.contains("Parse") || cn.contains("XML")) {
                return "parse";
            }
        }
        return "error";
    }

    /** 懒初始化 news 专用 client（带超时 + 复用全局代理直连规则）。 */
    private RestClient client() {
        RestClient c = newsHttpClient;
        if (c == null) {
            synchronized (this) {
                if (newsHttpClient == null) {
                    newsHttpClient = buildClient();
                }
                c = newsHttpClient;
            }
        }
        return c;
    }

    private RestClient buildClient() {
        NewsConfig.Fetch f = newsConfig.getFetch();
        RequestConfig rc = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(Math.max(500, f.getConnectTimeoutMs())))
                .setResponseTimeout(Timeout.ofMilliseconds(Math.max(1000, f.getReadTimeoutMs())))
                .build();
        HttpClientBuilder builder = HttpClients.custom().setDefaultRequestConfig(rc);
        // 复用全局策略：外网走 clash 代理、集群/内网地址直连（否则 rsshub 等内网名被转代理 → 502）
        if (StringUtils.isNoneBlank(proxyIp) && proxyPort != null) {
            HttpHost proxy = new HttpHost(proxyIp, proxyPort);
            builder.setRoutePlanner(new DefaultProxyRoutePlanner(proxy) {
                @Override
                protected HttpHost determineProxy(HttpHost target, HttpContext context) {
                    return RestClientConfig.isInternalHost(target.getHostName()) ? null : proxy;
                }
            });
        }
        CloseableHttpClient httpClient = builder.build();
        HttpComponentsClientHttpRequestFactory rf = new HttpComponentsClientHttpRequestFactory(httpClient);
        return RestClient.builder().requestFactory(rf).build();
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

    /**
     * 按 XML 声明的 encoding 把字节解码为字符串；未声明或字符集非法时默认 UTF-8。
     * 绝大多数 RSS 为 UTF-8，个别中文源可能是 GBK，靠此正确还原。
     */
    static String decodeXml(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // 仅用 ASCII 读取前若干字节探测 XML 声明（声明本身是 ASCII 安全的）
        int probeLen = Math.min(bytes.length, 256);
        String head = new String(bytes, 0, probeLen, StandardCharsets.US_ASCII);
        Charset charset = StandardCharsets.UTF_8;
        Matcher m = XML_ENCODING.matcher(head);
        if (m.find()) {
            try {
                charset = Charset.forName(m.group(1).trim());
            } catch (Exception ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return new String(bytes, charset);
    }
}
