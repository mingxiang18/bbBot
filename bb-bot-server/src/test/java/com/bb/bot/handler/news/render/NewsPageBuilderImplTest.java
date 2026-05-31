package com.bb.bot.handler.news.render;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.ReportMeta;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NewsPageBuilderImpl} 的 golden + 断言型测试（T4）。
 *
 * <p>{@code buildDaily} 是纯字符串拼接：给定固定的 {@link DailyReport} 与
 * {@link ReportMeta} 列表，输出完全确定。本测试用一个含 3 个分类（其中 1 个英文源、
 * 1 条 {@code mergedCount>1}）的 fixture + 两天可用日期生成 HTML，与 golden 文件做
 * 字节级比对（写法参照 {@code SplatoonHtmlRendererBattleCardGoldenTest}）；另加若干
 * 不依赖 golden 的结构断言（真链接 / 计数 / DOM 钩子 / HTML 转义）。</p>
 */
class NewsPageBuilderImplTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");

    private NewsPageBuilderImpl newBuilder(int perCategoryLimit) {
        NewsConfig cfg = new NewsConfig();
        cfg.setPerCategoryLimit(perCategoryLimit);
        return new NewsPageBuilderImpl(cfg);
    }

    /** 固定 fixture：3 分类（AI / 国际 / 苹果），含 1 个英文源、1 条多源合并。 */
    private DailyReport fixtureReport() {
        List<CuratedItem> items = List.of(
                // AI：中文源 + 英文源
                new CuratedItem(
                        "比亚迪自研AI芯片来了", "https://www.qbitai.com/2026/05/426557.html",
                        "量子位", "AI", "智驾出事，比亚迪兜底", 5, false, 1, ""),
                new CuratedItem(
                        "One company reportedly spent $500 million on Claude",
                        "https://the-decoder.com/500m-claude/",
                        "The Decoder", "AI",
                        "一家未具名公司被曝单月在 Claude 授权上花费 5 亿美元。",
                        4, true, 1, ""),
                // 国际：1 条多源合并（mergedCount>1 + note）
                new CuratedItem(
                        "黎以在美谈判未就停火达成一致",
                        "https://www.chinanews.com.cn/gj/2026/05-30/10631330.shtml",
                        "中新网", "国际",
                        "黎以军方代表团在美谈判未能就停火达成一致。",
                        5, false, 3, "多源合并"),
                // 苹果：含特殊字符的链接（& 需转义）
                new CuratedItem(
                        "Apple Music could soon get different subscription tiers",
                        "https://9to5mac.com/x?a=1&b=2",
                        "9to5Mac", "苹果",
                        "测试版字符串显示 Apple 正为 Apple Music 准备多级订阅方案。",
                        3, true, 1, "")
        );
        return new DailyReport(
                "2026-05-30",
                "<b>🤖 AI</b>：比亚迪自研芯片。<b>🌏 国际</b>：黎以停火谈判破裂。",
                items, 4, 4);
    }

    private List<ReportMeta> fixtureDates() {
        // 倒序（约定）
        return List.of(
                new ReportMeta("2026-05-30", 4, 4, "/news/2026-05-30.html"),
                new ReportMeta("2026-05-29", 6, 5, "/news/2026-05-29.html")
        );
    }

    @Test
    void buildDaily_matchesGolden() throws Exception {
        String html = newBuilder(5).buildDaily(fixtureReport(), fixtureDates());
        assertMatchesGolden("news-daily.html", html);
    }

    @Test
    void buildDaily_allTitleLinksAreRealHttpUrls() {
        String html = newBuilder(5).buildDaily(fixtureReport(), fixtureDates());
        Matcher m = Pattern.compile("<div class=\"title\"><a href=\"([^\"]+)\"").matcher(html);
        int count = 0;
        while (m.find()) {
            count++;
            assertThat(m.group(1)).startsWith("http");
        }
        assertThat(count).isEqualTo(4);
    }

    @Test
    void buildDaily_categoryCountsAreCorrect() {
        String html = newBuilder(5).buildDaily(fixtureReport(), fixtureDates());
        // 全部 4 条；AI 2、国际 1、苹果 1
        assertThat(html).contains(">全部 4</div>");
        assertThat(html).contains("data-cat=\"AI\">🤖 AI 2</div>");
        assertThat(html).contains("data-cat=\"国际\">🌏 国际 1</div>");
        assertThat(html).contains("data-cat=\"苹果\">🍎 苹果 1</div>");
        // 没有条目的分类不出 Tab
        assertThat(html).doesNotContain("data-cat=\"游戏\"");
    }

    @Test
    void buildDaily_hasInteractionDomHooks() {
        String html = newBuilder(5).buildDaily(fixtureReport(), fixtureDates());
        // 搜索框
        assertThat(html).contains("id=\"search\"");
        assertThat(html).contains("id=\"search-clear\"");
        // 分类 Tab 容器 + 计数
        assertThat(html).contains("id=\"tabs\"");
        assertThat(html).contains("id=\"count\"");
        // 跨天导航：日期下拉 + 注入的 JS 日期数组 + 当前日期
        assertThat(html).contains("id=\"date-picker\"");
        assertThat(html).contains("const DATES=['2026-05-30','2026-05-29'];");
        assertThat(html).contains("const CURRENT='2026-05-30';");
        // 每张卡片有搜索索引钩子
        assertThat(html).contains("data-search=");
    }

    @Test
    void buildDaily_escapesHtmlInData() {
        CuratedItem evil = new CuratedItem(
                "<script>alert('xss')</script>",
                "https://evil.test/x?a=1&b=2",
                "源<b>名</b>", "科技",
                "摘要 <img src=x onerror=alert(1)>", 3, false, 1, "");
        DailyReport report = new DailyReport(
                "2026-05-30", "导语", List.of(evil), 1, 1);
        String html = newBuilder(5).buildDaily(report, fixtureDates());

        assertThat(html).contains("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert('xss')</script>");
        assertThat(html).contains("源&lt;b&gt;名&lt;/b&gt;");
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
        // 链接里的 & 被转义为 &amp;
        assertThat(html).contains("href=\"https://evil.test/x?a=1&amp;b=2\"");
    }

    @Test
    void buildDaily_respectsPerCategoryLimit() {
        // perCategoryLimit=1：AI 两条只保留一条
        String html = newBuilder(1).buildDaily(fixtureReport(), fixtureDates());
        assertThat(html).contains("data-cat=\"AI\">🤖 AI 1</div>");
        assertThat(html).contains(">全部 3</div>");
    }

    @Test
    void buildDaily_sortsByImportanceWithinCategory_keepsTopWhenLimited() {
        // 同一分类 3 条，importance 乱序 [2,5,3]
        List<CuratedItem> items = List.of(
                new CuratedItem("标题甲低", "https://t.test/a", "源", "科技", "摘要甲", 2, false, 1, ""),
                new CuratedItem("标题乙高", "https://t.test/b", "源", "科技", "摘要乙", 5, false, 1, ""),
                new CuratedItem("标题丙中", "https://t.test/c", "源", "科技", "摘要丙", 3, false, 1, "")
        );
        DailyReport report = new DailyReport("2026-05-30", "导语", items, 1, 3);

        // perCategoryLimit=2 → 应保留 importance 5(乙) 和 3(丙)，丢掉 2(甲)；且乙在丙之前
        String html = newBuilder(2).buildDaily(report, fixtureDates());

        assertThat(html).contains("data-cat=\"科技\">🔬 科技 2</div>");
        assertThat(html).contains("标题乙高").contains("标题丙中");
        assertThat(html).doesNotContain("标题甲低");                 // importance 最低被截断
        assertThat(html.indexOf("标题乙高")).isLessThan(html.indexOf("标题丙中")); // 高分在前
    }

    @Test
    void buildArchiveIndex_listsDatesDescendingWithLinks() {
        String html = newBuilder(5).buildArchiveIndex(List.of(
                new ReportMeta("2026-05-29", 6, 5, "/news/2026-05-29.html"),
                new ReportMeta("2026-05-30", 4, 4, "/news/2026-05-30.html")
        ));
        assertThat(html).contains("href=\"./2026-05-30.html\"");
        assertThat(html).contains("href=\"./2026-05-29.html\"");
        assertThat(html).contains("4 条 · 4 源");
        // 倒序：30 号在 29 号之前
        assertThat(html.indexOf("2026-05-30")).isLessThan(html.indexOf("2026-05-29"));
    }

    private void assertMatchesGolden(String fileName, String actual) throws Exception {
        String resourcePath = "golden/" + fileName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                Files.createDirectories(GOLDEN_DIR);
                Files.write(GOLDEN_DIR.resolve(fileName), actual.getBytes(StandardCharsets.UTF_8));
                throw new IllegalStateException("golden 不存在，已生成 " + GOLDEN_DIR.resolve(fileName)
                        + "，请确认后重跑（提交前应已固化）。");
            }
            String expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actual).isEqualTo(expected);
        }
    }
}
