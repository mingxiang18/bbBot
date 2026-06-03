package com.bb.bot.handler.news.curate;

import com.bb.bot.common.util.aiChat.provider.AIException;
import com.bb.bot.common.util.aiChat.provider.AIProviderProperties;
import com.bb.bot.common.util.aiChat.provider.ChatMessage;
import com.bb.bot.common.util.aiChat.provider.ModelSpec;
import com.bb.bot.common.util.aiChat.provider.ProviderDispatcher;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsCategory;
import com.bb.bot.handler.news.contract.NewsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NewsAiCuratorImpl} 单测：正常解析 / 排序 / normalize，以及三种降级路径。
 */
class NewsAiCuratorImplTest {

    private ProviderDispatcher providerDispatcher;
    private AIProviderProperties aiProviderProperties;
    private NewsConfig newsConfig;
    private NewsAiCuratorImpl curator;

    @BeforeEach
    void setUp() {
        providerDispatcher = mock(ProviderDispatcher.class);

        // 真实构造 AIProviderProperties + ModelSpec
        aiProviderProperties = new AIProviderProperties();
        ModelSpec lightSpec = new ModelSpec();
        lightSpec.setName("ds-flash");
        lightSpec.setBaseUrl("https://api.deepseek.com/v1");
        lightSpec.setApiKey("sk-test");
        lightSpec.setModel("deepseek-chat");
        lightSpec.setKind("deepseek");
        aiProviderProperties.getModels().put("ds-flash", lightSpec);
        aiProviderProperties.getRoles().setLight("ds-flash");
        aiProviderProperties.getRoles().setHeavy("ds-flash");

        newsConfig = new NewsConfig();
        newsConfig.getAi().setEnabled(true);
        newsConfig.getAi().setRole("light");
        newsConfig.getAi().setMaxItems(40);

        curator = new NewsAiCuratorImpl();
        ReflectionTestUtils.setField(curator, "providerDispatcher", providerDispatcher);
        ReflectionTestUtils.setField(curator, "aiProviderProperties", aiProviderProperties);
        ReflectionTestUtils.setField(curator, "newsConfig", newsConfig);
    }

    private NewsItem zhItem() {
        return new NewsItem("中新网", "时政", "国内某要闻",
                "https://example.com/a", "原始中文摘要", "Mon, 01 Jan 2026", "zh", "h1");
    }

    private NewsItem enItem() {
        return new NewsItem("Reuters", "国际", "Some English Headline",
                "https://example.com/b", "raw english desc", "Mon, 01 Jan 2026", "en", "h2");
    }

    private static NewsItem src(String source, String cat, String title, String link, String lang) {
        return new NewsItem(source, cat, title, link, "原始摘要-" + title, "Mon, 01 Jan 2026", lang, "h-" + title);
    }

    @Test
    void curate_parsesById_backfillsRealFields_normalizesCategory_andSorts() throws Exception {
        // 4 个不同源条目 → id n1..n4（interleaveBySource 保持顺序）
        List<NewsItem> input = List.of(
                src("Reuters", "国际", "Intl A", "https://r/a", "en"),     // n1
                src("某娱乐", "娱乐", "娱乐X", "https://e/x", "zh"),          // n2
                src("机器之心", "AI", "AI高", "https://m/h", "zh"),          // n3
                src("量子位", "AI", "AI低", "https://q/l", "zh"));          // n4

        // 关键：JSON 里塞入伪造的 title/link/sourceName，断言结果一律用服务端按 id 回填的真实值
        String json = """
                ```json
                {
                  "brief": "今日速览导语",
                  "items": [
                    {"id":"n1","title":"伪造标题","link":"https://evil/x","sourceName":"伪造来源",
                     "category":"国际","summaryZh":"国际摘要A","importance":5,"clusterIds":["n1","n2"],"note":"去重 2→1"},
                    {"id":"n2","category":"娱乐","summaryZh":"娱乐摘要","importance":4,"clusterIds":["n2"],"note":""},
                    {"id":"n3","category":"AI","summaryZh":"AI摘要高","importance":5,"clusterIds":["n3"],"note":""},
                    {"id":"n4","category":"AI","summaryZh":"AI摘要低","importance":2,"clusterIds":["n4"],"note":""}
                  ]
                }
                ```
                """;
        when(providerDispatcher.chat(any(ModelSpec.class), anyList())).thenReturn(json);

        DailyReport report = curator.curate(input);

        assertThat(report.brief()).isEqualTo("今日速览导语");
        assertThat(report.totalCount()).isEqualTo(4);
        assertThat(report.sourceCount()).isEqualTo(4);
        assertThat(report.date()).isEqualTo(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        List<CuratedItem> items = report.items();
        // 排序：AI(高 imp5 n3) → AI(低 imp2 n4) → 国际(n1) → 越界娱乐(科技/FALLBACK n2)
        assertThat(items.get(0).category()).isEqualTo(NewsCategory.AI);
        assertThat(items.get(0).importance()).isEqualTo(5);
        assertThat(items.get(0).title()).isEqualTo("AI高");
        assertThat(items.get(1).category()).isEqualTo(NewsCategory.AI);
        assertThat(items.get(1).importance()).isEqualTo(2);
        assertThat(items.get(2).category()).isEqualTo(NewsCategory.WORLD);
        assertThat(items.get(3).category()).isEqualTo(NewsCategory.FALLBACK);
        assertThat(items.get(3).title()).isEqualTo("娱乐X");

        // n1：服务端回填，伪造字段被忽略
        CuratedItem world = items.get(2);
        assertThat(world.title()).isEqualTo("Intl A");
        assertThat(world.link()).isEqualTo("https://r/a");
        assertThat(world.sourceName()).isEqualTo("Reuters");
        assertThat(world.link()).isNotEqualTo("https://evil/x");
        assertThat(world.english()).isTrue();            // 来自 src.lang=en
        assertThat(world.mergedCount()).isEqualTo(2);    // clusterIds 数量
        assertThat(world.note()).isEqualTo("去重 2→1");
        assertThat(world.summaryZh()).isEqualTo("国际摘要A");
    }

    @Test
    void curate_dropsUnknownAndDuplicateIds_andFabricatedLinks() throws Exception {
        List<NewsItem> input = List.of(
                src("机器之心", "AI", "真AI", "https://m/real", "zh"));   // n1 only

        // n1 正常；n1 重复（应丢）；n99 不存在（凭空捏造，应丢）
        String json = """
                {"brief":"b","items":[
                  {"id":"n1","category":"AI","summaryZh":"摘要","importance":4,"clusterIds":["n1"]},
                  {"id":"n1","category":"AI","summaryZh":"重复","importance":3},
                  {"id":"n99","title":"幻觉新闻","link":"https://hallucinated/x","category":"国际","summaryZh":"假","importance":5}
                ]}
                """;
        when(providerDispatcher.chat(any(ModelSpec.class), anyList())).thenReturn(json);

        DailyReport report = curator.curate(input);

        // 只剩 n1 一条；幻觉链接不出现
        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).title()).isEqualTo("真AI");
        assertThat(report.items().get(0).link()).isEqualTo("https://m/real");
        assertThat(report.items()).extracting(CuratedItem::link)
                .doesNotContain("https://hallucinated/x");
    }

    @Test
    void curate_emptySummary_fallsBackToOriginalDescription() throws Exception {
        List<NewsItem> input = List.of(src("源", "AI", "标题T", "https://s/1", "zh"));
        String json = "{\"brief\":\"b\",\"items\":[{\"id\":\"n1\",\"category\":\"AI\",\"summaryZh\":\"\",\"importance\":3}]}";
        when(providerDispatcher.chat(any(ModelSpec.class), anyList())).thenReturn(json);

        DailyReport report = curator.curate(input);

        assertThat(report.items()).hasSize(1);
        // 空摘要回退到原文摘要 "原始摘要-标题T"
        assertThat(report.items().get(0).summaryZh()).isEqualTo("原始摘要-标题T");
    }

    @Test
    void curate_whenChatThrowsAIException_fallsBackWithoutThrowing() throws Exception {
        when(providerDispatcher.chat(any(ModelSpec.class), anyList()))
                .thenThrow(new AIException(AIException.ErrorType.RETRYABLE, "boom"));

        List<NewsItem> input = List.of(zhItem(), enItem());
        DailyReport report = curator.curate(input);

        // 降级页面 brief 显式标记，不再为空
        assertThat(report.brief()).isEqualTo(NewsAiCuratorImpl.FALLBACK_BRIEF);
        assertThat(report.totalCount()).isEqualTo(2);
        assertThat(report.items()).hasSize(2);
        // 降级映射：summaryZh = 原 description, importance=3
        assertThat(report.items()).allSatisfy(it -> assertThat(it.importance()).isEqualTo(3));
        assertThat(report.items()).extracting(CuratedItem::summaryZh)
                .containsExactlyInAnyOrder("原始中文摘要", "raw english desc");
        // english 来自 lang
        CuratedItem en = report.items().stream()
                .filter(i -> i.sourceName().equals("Reuters")).findFirst().orElseThrow();
        assertThat(en.english()).isTrue();
    }

    @Test
    void curate_whenAiDisabled_fallsBackAndNeverCallsChat() throws Exception {
        newsConfig.getAi().setEnabled(false);

        List<NewsItem> input = List.of(zhItem(), enItem());
        DailyReport report = curator.curate(input);

        assertThat(report.brief()).isEqualTo(NewsAiCuratorImpl.FALLBACK_BRIEF);
        assertThat(report.totalCount()).isEqualTo(2);
        assertThat(report.sourceCount()).isEqualTo(2);
        verify(providerDispatcher, never()).chat(any(), anyList());
    }

    @Test
    void curate_whenJsonUnparseable_fallsBack() throws Exception {
        when(providerDispatcher.chat(any(ModelSpec.class), anyList()))
                .thenReturn("抱歉，我无法完成这个任务。");

        DailyReport report = curator.curate(List.of(zhItem()));

        assertThat(report.brief()).isEqualTo(NewsAiCuratorImpl.FALLBACK_BRIEF);
        assertThat(report.totalCount()).isEqualTo(1);
    }

    @Test
    void curate_whenAiReturnsEmptyItems_isLegalEmpty_notRawFallback() throws Exception {
        // 模型显式判定当天无合格资讯：合法空精选，不得退化成降级（更不能 raw 全量）
        when(providerDispatcher.chat(any(ModelSpec.class), anyList()))
                .thenReturn("{\"brief\":\"今日暂无足够高价值资讯\",\"items\":[]}");

        DailyReport report = curator.curate(List.of(zhItem(), enItem()));

        assertThat(report.items()).isEmpty();
        assertThat(report.totalCount()).isZero();
        // brief 来自模型，而非降级标记
        assertThat(report.brief()).isEqualTo("今日暂无足够高价值资讯");
        assertThat(report.brief()).isNotEqualTo(NewsAiCuratorImpl.FALLBACK_BRIEF);
    }

    @Test
    void fallback_isConservative_capsPerSource_dropsLowValueAndBadLinks() {
        // 强制降级（AI 未启用），构造：单源多条 + 低价值标题 + 非法链接
        newsConfig.getAi().setEnabled(false);
        List<NewsItem> input = new ArrayList<>();
        // 澎湃 5 条合法 → 每源上限 2，只保留 2
        for (int i = 0; i < 5; i++) {
            input.add(new NewsItem("澎湃", "时政", "正常新闻" + i,
                    "https://p.com/" + i, "摘要", "", "zh", "ph" + i));
        }
        // 低价值标题 → 丢弃
        input.add(new NewsItem("中新", "时政", "双十一促销大放送",
                "https://c.com/ad", "摘要", "", "zh", "c1"));
        // 中新一条合法 → 保留（中新计数 1）
        input.add(new NewsItem("中新", "时政", "国内要闻",
                "https://c.com/news", "摘要", "", "zh", "c2"));
        // 非法链接 → 丢弃
        input.add(new NewsItem("网易", "时政", "标题正常但链接坏",
                "ftp://bad/x", "摘要", "", "zh", "w1"));

        DailyReport report = curator.curate(input);

        // 保留 = 澎湃 2 + 中新 1 = 3
        assertThat(report.items()).hasSize(3);
        assertThat(report.items()).extracting(CuratedItem::title)
                .noneMatch(t -> t.contains("促销"));
        assertThat(report.items()).extracting(CuratedItem::link)
                .allMatch(l -> l.startsWith("https://"));
        assertThat(report.items()).allSatisfy(it -> assertThat(it.importance()).isEqualTo(3));
        assertThat(report.brief()).isEqualTo(NewsAiCuratorImpl.FALLBACK_BRIEF);

        // 进一步收紧总量上限：fallbackMaxItems=2 时整体只剩 2 条
        newsConfig.getAi().setFallbackMaxItems(2);
        DailyReport capped = curator.curate(input);
        assertThat(capped.items()).hasSize(2);
    }

    @Test
    void curate_whenModelNotConfigured_fallsBackWithoutCallingChat() throws Exception {
        // 清空 models 表，使 role 解析不到 spec
        AIProviderProperties empty = new AIProviderProperties();
        empty.getRoles().setLight("missing");
        empty.getRoles().setHeavy("missing");
        ReflectionTestUtils.setField(curator, "aiProviderProperties", empty);

        DailyReport report = curator.curate(List.of(zhItem(), enItem()));

        assertThat(report.totalCount()).isEqualTo(2);
        verify(providerDispatcher, never()).chat(any(), anyList());
    }

    // ---- 按源轮转（修复 maxItems 只喂前几个源的缺陷） ----

    private static NewsItem ni(String source, String title) {
        return new NewsItem(source, "时政", title, "https://x/" + title, "desc", "", "zh", title);
    }

    @Test
    void interleaveBySource_roundRobinBalancesSources() {
        List<NewsItem> in = List.of(
                ni("A", "a1"), ni("A", "a2"), ni("A", "a3"),
                ni("B", "b1"),
                ni("C", "c1"), ni("C", "c2"));

        List<NewsItem> out = NewsAiCuratorImpl.interleaveBySource(in);

        // 轮转顺序 A,B,C,A,C,A；组内相对顺序保持
        assertThat(out).extracting(NewsItem::sourceName)
                .containsExactly("A", "B", "C", "A", "C", "A");
        assertThat(out).extracting(NewsItem::title)
                .containsExactly("a1", "b1", "c1", "a2", "c2", "a3");
    }

    @Test
    void interleaveBySource_truncationNowCoversAllSources() {
        // 真实问题复现：源1有 30 条、后面源各 1 条。旧逻辑前 N 条全是源1。
        List<NewsItem> in = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            in.add(ni("澎湃", "p" + i));
        }
        in.add(ni("量子位", "q1"));
        in.add(ni("机核", "g1"));

        List<NewsItem> out = NewsAiCuratorImpl.interleaveBySource(in);

        // 轮转后前 3 条就覆盖了 3 个源（旧逻辑这 3 条全是澎湃，量子位/机核被饿死）
        assertThat(out.subList(0, 3)).extracting(NewsItem::sourceName)
                .containsExactly("澎湃", "量子位", "机核");
    }

    @Test
    void interleaveBySource_emptyOrNull_safe() {
        assertThat(NewsAiCuratorImpl.interleaveBySource(null)).isEmpty();
        assertThat(NewsAiCuratorImpl.interleaveBySource(List.of())).isEmpty();
    }

    @Test
    void curatePrompt_instructsSelectionAndDropTrivial() {
        String sys = CuratePrompt.system();
        assertThat(sys).contains("精选").contains("丢弃");
    }
}
