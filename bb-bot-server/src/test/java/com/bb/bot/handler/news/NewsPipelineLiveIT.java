package com.bb.bot.handler.news;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsItem;
import com.bb.bot.handler.news.contract.ReportMeta;
import com.bb.bot.handler.news.curate.NewsAiCuratorImpl;
import com.bb.bot.handler.news.fetch.NewsFetcherImpl;
import com.bb.bot.handler.news.hosting.NewsHostingImpl;
import com.bb.bot.handler.news.render.NewsPageBuilderImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活体集成测试：真实网络源 → 真实解析 → AI 降级整理（不依赖 LLM 密钥）→ 真实渲染 → 真实落盘。
 *
 * <p><b>不进默认 {@code mvn test} 与 release 构建</b>（类名以 {@code IT} 结尾，不匹配 surefire 默认
 * {@code *Test} 包含规则），仅在显式指定时运行：</p>
 * <pre>mvn -pl bb-bot-server -o -Dtest=NewsPipelineLiveIT -Dsurefire.failIfNoSpecifiedTests=false test</pre>
 *
 * <p>验证真实运行链路（采集/解析/渲染/落盘），AI 整理走降级（真实代码路径，但不调用外部 LLM），
 * 产出的 HTML 同时写到 {@code /tmp/news_live_output.html} 供人工核对。</p>
 */
class NewsPipelineLiveIT {

    private static NewsConfig.Source src(String name, String cat, String url, String lang) {
        NewsConfig.Source s = new NewsConfig.Source();
        s.setName(name);
        s.setCategory(cat);
        s.setVia("direct");
        s.setUrl(url);
        s.setLang(lang);
        return s;
    }

    @Test
    void livePipeline_realDirectSources_producesRealHtml() throws Exception {
        // --- 配置：4 个已实测存活的官方直连源 ---
        NewsConfig cfg = new NewsConfig();
        cfg.setPerSourceLimit(8);
        cfg.getAi().setEnabled(false); // 降级：不依赖 LLM 密钥，但走真实降级代码路径
        cfg.setSources(List.of(
                src("中新网国际", "国际", "https://www.chinanews.com.cn/rss/world.xml", "zh"),
                src("少数派", "科技", "https://sspai.com/feed", "zh"),
                src("36氪快讯", "财经", "https://36kr.com/feed-newsflash", "zh"),
                src("BBC中文", "国际", "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml", "zh")
        ));

        // --- T2 采集（真实网络，并发 + 真实超时；client 由 fetcher 懒建） ---
        NewsFetcherImpl fetcher = new NewsFetcherImpl();
        ReflectionTestUtils.setField(fetcher, "newsConfig", cfg);
        List<NewsItem> items = fetcher.fetchAll();
        System.out.println("[LIVE] 采集到条目数 = " + items.size());
        assertThat(items).as("真实源应采集到条目").isNotEmpty();
        // 每条都有真实文章链接与去重键
        assertThat(items).allSatisfy(it -> {
            assertThat(it.link()).startsWith("http");
            assertThat(it.linkHash()).hasSize(40);
        });

        // --- T3 AI 整理（降级路径，真实代码） ---
        NewsAiCuratorImpl curator = new NewsAiCuratorImpl();
        ReflectionTestUtils.setField(curator, "newsConfig", cfg);
        // providerDispatcher / aiProviderProperties 在 enabled=false 时不会被使用，注 null 安全
        DailyReport report = curator.curate(items);
        assertThat(report.items()).isNotEmpty();
        assertThat(report.totalCount()).isEqualTo(report.items().size());
        System.out.println("[LIVE] 整理后条目数 = " + report.totalCount()
                + "，源数 = " + report.sourceCount());

        // --- T4 渲染（真实 HTML） ---
        NewsPageBuilderImpl builder = new NewsPageBuilderImpl(cfg);
        List<ReportMeta> dates = new ArrayList<>();
        dates.add(new ReportMeta(report.date(), report.totalCount(), report.sourceCount(),
                "/news/" + report.date() + ".html"));
        String dailyHtml = builder.buildDaily(report, dates);
        String indexHtml = builder.buildArchiveIndex(dates);
        assertThat(dailyHtml).contains("<html").contains("每日资讯");
        // 渲染出的原文链接是真实 http 链接
        assertThat(dailyHtml).contains("href=\"http");

        // --- T5 落盘（真实文件） ---
        Path dir = Files.createTempDirectory("news-live");
        NewsConfig hostCfg = new NewsConfig();
        hostCfg.getHosting().setDir(dir.toString());
        hostCfg.getHosting().setPublicBase("/news");
        NewsHostingImpl hosting = new NewsHostingImpl();
        ReflectionTestUtils.setField(hosting, "newsConfig", hostCfg);
        String url = hosting.publish(report.date(), dailyHtml, indexHtml);
        assertThat(url).isEqualTo("/news/" + report.date() + ".html");
        assertThat(Files.exists(dir.resolve(report.date() + ".html"))).isTrue();
        assertThat(Files.exists(dir.resolve("index.html"))).isTrue();
        assertThat(Files.exists(dir.resolve("archive.html"))).isTrue();

        // --- 产物落到 /tmp 供人工核对 ---
        Path inspect = Path.of("/tmp/news_live_output.html");
        Files.write(inspect, dailyHtml.getBytes(StandardCharsets.UTF_8));
        System.out.println("[LIVE] HTML 字节数 = " + dailyHtml.length()
                + "，已写出供核对：" + inspect);
    }
}
