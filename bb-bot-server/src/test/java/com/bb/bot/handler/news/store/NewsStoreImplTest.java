package com.bb.bot.handler.news.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.database.news.entity.NewsDailyPo;
import com.bb.bot.database.news.entity.NewsItemPo;
import com.bb.bot.database.news.service.INewsDailyService;
import com.bb.bot.database.news.service.INewsItemService;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsItem;
import com.bb.bot.handler.news.contract.NewsReviewState;
import com.bb.bot.handler.news.contract.ReportMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NewsStoreImpl} 单测：去重保存 / 归档列表 / 日报读取。
 */
class NewsStoreImplTest {

    private INewsItemService newsItemService;
    private INewsDailyService newsDailyService;
    private NewsConfig newsConfig;
    private NewsStoreImpl store;

    @BeforeEach
    void setUp() {
        newsItemService = mock(INewsItemService.class);
        newsDailyService = mock(INewsDailyService.class);
        newsConfig = new NewsConfig();
        newsConfig.getHosting().setPublicBase("/news");

        store = new NewsStoreImpl();
        ReflectionTestUtils.setField(store, "newsItemService", newsItemService);
        ReflectionTestUtils.setField(store, "newsDailyService", newsDailyService);
        ReflectionTestUtils.setField(store, "newsConfig", newsConfig);
    }

    private NewsItem item(String title, String hash) {
        return new NewsItem("源", "科技", title,
                "https://example.com/" + title, "desc", "2026-05-30", "zh", hash);
    }

    @Test
    void dedupAndSave_filtersExisting_insertsOnlyFresh() {
        NewsItem a = item("a", "hash-a");
        NewsItem b = item("b", "hash-b");
        NewsItem c = item("c", "hash-c");

        // hash-b 已存在
        NewsItemPo existing = new NewsItemPo();
        existing.setLinkHash("hash-b");
        when(newsItemService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        when(newsItemService.saveBatch(any())).thenReturn(true);

        List<NewsItem> fresh = store.dedupAndSave(List.of(a, b, c));

        assertThat(fresh).extracting(NewsItem::linkHash)
                .containsExactly("hash-a", "hash-c");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<NewsItemPo>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(newsItemService, times(1)).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(NewsItemPo::getLinkHash)
                .containsExactly("hash-a", "hash-c");
        assertThat(captor.getValue()).allSatisfy(po ->
                assertThat(po.getReportDate()).isEqualTo(LocalDate.now()));
    }

    @Test
    void listRecent_buildsMetasDescWithUrl() {
        NewsDailyPo d1 = new NewsDailyPo();
        d1.setReportDate(LocalDate.of(2026, 5, 30));
        d1.setTotalCount(10);
        d1.setSourceCount(4);
        NewsDailyPo d2 = new NewsDailyPo();
        d2.setReportDate(LocalDate.of(2026, 5, 29));
        d2.setTotalCount(8);
        d2.setSourceCount(3);

        // mapper 已按 report_date 倒序返回
        when(newsDailyService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(d1, d2));

        List<ReportMeta> metas = store.listRecent(7);

        assertThat(metas).hasSize(2);
        assertThat(metas.get(0).date()).isEqualTo("2026-05-30");
        assertThat(metas.get(0).url()).isEqualTo("/news/2026-05-30.html");
        assertThat(metas.get(0).totalCount()).isEqualTo(10);
        assertThat(metas.get(0).sourceCount()).isEqualTo(4);
        assertThat(metas.get(1).date()).isEqualTo("2026-05-29");
        assertThat(metas.get(1).url()).isEqualTo("/news/2026-05-29.html");
    }

    @Test
    void getReport_assemblesItemsByImportanceDesc() {
        NewsDailyPo daily = new NewsDailyPo();
        daily.setReportDate(LocalDate.of(2026, 5, 30));
        daily.setBrief("今日速览");
        daily.setTotalCount(2);
        daily.setSourceCount(2);
        when(newsDailyService.getById(LocalDate.of(2026, 5, 30))).thenReturn(daily);

        // mapper 已按 importance 倒序返回
        NewsItemPo high = new NewsItemPo();
        high.setTitle("Big news");
        high.setLink("https://example.com/big");
        high.setSourceName("Reuters");
        high.setCategory("国际");
        high.setSummaryZh("重大新闻");
        high.setImportance(5);
        high.setMergedCount(2);
        high.setLang("en");

        NewsItemPo low = new NewsItemPo();
        low.setTitle("小新闻");
        low.setLink("https://example.com/small");
        low.setSourceName("中新网");
        low.setCategory("科技");
        low.setSummaryZh("一般新闻");
        low.setImportance(2);
        low.setMergedCount(1);
        low.setLang("zh");

        when(newsItemService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(high, low));

        DailyReport report = store.getReport("2026-05-30");

        assertThat(report).isNotNull();
        assertThat(report.date()).isEqualTo("2026-05-30");
        assertThat(report.brief()).isEqualTo("今日速览");
        assertThat(report.totalCount()).isEqualTo(2);
        assertThat(report.sourceCount()).isEqualTo(2);
        assertThat(report.items()).hasSize(2);
        assertThat(report.items().get(0).importance()).isEqualTo(5);
        assertThat(report.items().get(0).english()).isTrue();
        assertThat(report.items().get(0).note()).isEqualTo("");
        assertThat(report.items().get(1).importance()).isEqualTo(2);
        assertThat(report.items().get(1).english()).isFalse();
    }

    @Test
    void getReport_returnsNullWhenAbsent() {
        when(newsDailyService.getById(any())).thenReturn(null);
        assertThat(store.getReport("2026-05-30")).isNull();
    }

    // ---- Phase 2：候选生命周期 ----

    @Test
    void dedupAndSave_touchesLastSeenOfExisting_andInsertsFreshAsRaw() {
        NewsItem a = item("a", "hash-a");
        NewsItem b = item("b", "hash-b"); // 已存在

        NewsItemPo existing = new NewsItemPo();
        existing.setLinkHash("hash-b");
        when(newsItemService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        when(newsItemService.saveBatch(any())).thenReturn(true);

        store.dedupAndSave(List.of(a, b));

        // 已存在条目走 last_seen 刷新（update(entity, 惰性 wrapper)）
        verify(newsItemService, atLeastOnce())
                .update(any(NewsItemPo.class), any(LambdaQueryWrapper.class));

        // 新条目以 RAW + first/last_seen 入库
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<NewsItemPo>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(newsItemService, times(1)).saveBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        NewsItemPo inserted = captor.getValue().get(0);
        assertThat(inserted.getLinkHash()).isEqualTo("hash-a");
        assertThat(inserted.getReviewState()).isEqualTo(NewsReviewState.RAW);
        assertThat(inserted.getFirstSeenAt()).isNotNull();
        assertThat(inserted.getLastSeenAt()).isNotNull();
    }

    @Test
    void listEligibleForReport_mapsRows_andAppliesPerSourceWindow() {
        // 配置：慢源 window=72h，快源用默认 48h
        NewsConfig.Source slow = new NewsConfig.Source();
        slow.setName("慢源");
        slow.setWindowHours(72);
        NewsConfig.Source fast = new NewsConfig.Source();
        fast.setName("快源");
        fast.setWindowHours(0); // → 默认 48
        newsConfig.setSources(List.of(slow, fast));
        newsConfig.setCandidateWindowHours(48);

        LocalDateTime now = LocalDateTime.now();
        NewsItemPo slowOld = poOf("慢源", "https://s/1", "hh-s", now.minusHours(60)); // 60<72 → 保留
        NewsItemPo fastOld = poOf("快源", "https://f/1", "hh-f", now.minusHours(60)); // 60>48 → 丢弃
        NewsItemPo fastNew = poOf("快源", "https://f/2", "hh-f2", now.minusHours(10)); // 10<48 → 保留
        when(newsItemService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(slowOld, fastOld, fastNew));

        List<NewsItem> pool = store.listEligibleForReport("2026-06-03");

        assertThat(pool).extracting(NewsItem::linkHash)
                .containsExactlyInAnyOrder("hh-s", "hh-f2");
        assertThat(pool).extracting(NewsItem::linkHash).doesNotContain("hh-f");
    }

    private static NewsItemPo poOf(String source, String link, String hash, LocalDateTime firstSeen) {
        NewsItemPo po = new NewsItemPo();
        po.setSourceName(source);
        po.setCategory("科技");
        po.setTitle("t");
        po.setLink(link);
        po.setLinkHash(hash);
        po.setDescription("d");
        po.setLang("zh");
        po.setReviewState(NewsReviewState.RAW);
        po.setFirstSeenAt(firstSeen);
        return po;
    }
}
