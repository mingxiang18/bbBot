package com.bb.bot.handler.news.fetch;

import com.bb.bot.handler.news.contract.LinkHash;
import com.bb.bot.handler.news.contract.NewsItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RssParser} 单测。覆盖：条数、字段正确性、description 清洗、
 * linkHash 幂等 + 去 query 一致、缺字段降级、畸形/空 XML 不抛异常。
 */
class RssParserTest {

    private static final String SOURCE = "测试源";
    private static final String CATEGORY = "国际";
    private static final String LANG = "zh";

    private String loadFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/news/sample-rss.xml")) {
            assertThat(in).as("fixture /news/sample-rss.xml 存在").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parse_returnsAllItems_withCorrectFields() throws IOException {
        List<NewsItem> items = RssParser.parse(loadFixture(), SOURCE, CATEGORY, LANG);

        assertThat(items).hasSize(3);

        NewsItem first = items.get(0);
        assertThat(first.sourceName()).isEqualTo(SOURCE);
        assertThat(first.category()).isEqualTo(CATEGORY);
        assertThat(first.lang()).isEqualTo(LANG);
        assertThat(first.title()).isEqualTo("俄外交部称已召回驻亚美尼亚大使");
        assertThat(first.link())
                .isEqualTo("https://www.chinanews.com.cn/gj/2026/05-30/10631393.shtml?spm=rss&from=feed");
        assertThat(first.pubDate()).isEqualTo("Sat, 30 May 2026 14:51:26 +0800");
    }

    @Test
    void parse_cleansDescription_stripsHtmlAndTail() throws IOException {
        List<NewsItem> items = RssParser.parse(loadFixture(), SOURCE, CATEGORY, LANG);

        // 第一条：去掉「查看全文」尾巴 + 去 HTML 标签
        assertThat(items.get(0).description())
                .doesNotContain("查看全文")
                .doesNotContain("<a")
                .doesNotContain("href")
                .contains("俄罗斯外交部发布消息");

        // 第二条：CDATA 内的 <p> 标签被去掉
        assertThat(items.get(1).description())
                .doesNotContain("<p>")
                .isEqualTo("儘管最近發生了相互空襲，但雙方似乎都無意恢復全面衝突。");
    }

    @Test
    void parse_missingDescription_degradesToEmptyString() throws IOException {
        List<NewsItem> items = RssParser.parse(loadFixture(), SOURCE, CATEGORY, LANG);

        NewsItem noDesc = items.get(2);
        assertThat(noDesc.title()).isEqualTo("无摘要的条目");
        assertThat(noDesc.description()).isEqualTo("");
    }

    @Test
    void linkHash_is40LowerHex() throws IOException {
        List<NewsItem> items = RssParser.parse(loadFixture(), SOURCE, CATEGORY, LANG);

        for (NewsItem item : items) {
            assertThat(item.linkHash()).hasSize(40);
            assertThat(item.linkHash()).matches("[0-9a-f]{40}");
        }
    }

    @Test
    void linkHash_isIdempotent_forSameLink() {
        String hash1 = LinkHash.of("https://example.com/post/42");
        String hash2 = LinkHash.of("https://example.com/post/42");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void linkHash_ignoresQueryParams() {
        // 两个只差 query 的 link 必须得到相同 hash
        String a = "https://example.com/post/42?utm_source=rss&x=1";
        String b = "https://example.com/post/42?from=feed";
        String c = "https://example.com/post/42";

        String hashA = LinkHash.of(a);
        String hashB = LinkHash.of(b);
        String hashC = LinkHash.of(c);

        assertThat(hashA).isEqualTo(hashB).isEqualTo(hashC);
    }

    @Test
    void linkHash_ignoresFragment() {
        String withFragment = "https://example.com/post/42#section";
        String plain = "https://example.com/post/42";
        assertThat(LinkHash.of(withFragment))
                .isEqualTo(LinkHash.of(plain));
    }

    @Test
    void parse_fixtureLinks_differInQueryOnly_yieldSameHashAsCleanLink() throws IOException {
        List<NewsItem> items = RssParser.parse(loadFixture(), SOURCE, CATEGORY, LANG);

        // fixture 第一条 link 带 ?spm=rss&from=feed，其 hash 应等于去 query 后的 hash
        String expected = LinkHash.of(
                "https://www.chinanews.com.cn/gj/2026/05-30/10631393.shtml");
        assertThat(items.get(0).linkHash()).isEqualTo(expected);
    }

    @Test
    void parse_nullXml_returnsEmptyList() {
        assertThat(RssParser.parse(null, SOURCE, CATEGORY, LANG)).isEmpty();
    }

    @Test
    void parse_blankXml_returnsEmptyList() {
        assertThat(RssParser.parse("   ", SOURCE, CATEGORY, LANG)).isEmpty();
    }

    @Test
    void parse_malformedXml_doesNotThrow_returnsEmptyOrPartial() {
        String malformed = "<rss><channel><item><title>broken</title";
        List<NewsItem> items = RssParser.parse(malformed, SOURCE, CATEGORY, LANG);
        // jsoup 容错解析：不抛异常即达标；item 无 link 时会被跳过
        assertThat(items).isNotNull();
    }

    @Test
    void parse_nonRssXml_returnsEmptyList() {
        String html = "<html><head><title>Welcome to RSSHub!</title></head><body>no items</body></html>";
        assertThat(RssParser.parse(html, SOURCE, CATEGORY, LANG)).isEmpty();
    }

    @Test
    void parse_itemWithoutLink_isSkipped() {
        String xml = "<rss><channel>"
                + "<item><title>有链接</title><link>https://example.com/a</link></item>"
                + "<item><title>无链接</title></item>"
                + "</channel></rss>";
        List<NewsItem> items = RssParser.parse(xml, SOURCE, CATEGORY, LANG);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("有链接");
    }
}
