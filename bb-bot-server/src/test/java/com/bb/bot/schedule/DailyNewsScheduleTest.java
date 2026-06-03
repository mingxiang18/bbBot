package com.bb.bot.schedule;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsAiCurator;
import com.bb.bot.handler.news.contract.NewsFetcher;
import com.bb.bot.handler.news.contract.NewsHosting;
import com.bb.bot.handler.news.contract.NewsItem;
import com.bb.bot.handler.news.contract.NewsPageBuilder;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.handler.news.contract.ReportMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DailyNewsSchedule} 编排单测：纯 Mockito + AssertJ，验证调用顺序、短路分支与异常吞掉。
 */
class DailyNewsScheduleTest {

    private DailyNewsSchedule schedule;

    private NewsConfig newsConfig;
    private NewsFetcher newsFetcher;
    private NewsStore newsStore;
    private NewsAiCurator newsAiCurator;
    private NewsPageBuilder newsPageBuilder;
    private NewsHosting newsHosting;

    @BeforeEach
    void setUp() {
        schedule = new DailyNewsSchedule();
        newsConfig = mock(NewsConfig.class);
        newsFetcher = mock(NewsFetcher.class);
        newsStore = mock(NewsStore.class);
        newsAiCurator = mock(NewsAiCurator.class);
        newsPageBuilder = mock(NewsPageBuilder.class);
        newsHosting = mock(NewsHosting.class);

        ReflectionTestUtils.setField(schedule, "newsConfig", newsConfig);
        ReflectionTestUtils.setField(schedule, "newsFetcher", newsFetcher);
        ReflectionTestUtils.setField(schedule, "newsStore", newsStore);
        ReflectionTestUtils.setField(schedule, "newsAiCurator", newsAiCurator);
        ReflectionTestUtils.setField(schedule, "newsPageBuilder", newsPageBuilder);
        ReflectionTestUtils.setField(schedule, "newsHosting", newsHosting);
    }

    private NewsItem sampleItem() {
        return new NewsItem("中新网", "world", "标题", "https://a.com/1",
                "摘要", "Mon, 30 May 2026 08:00:00 GMT", "zh", "hash1");
    }

    private static CuratedItem curated(String title, String link) {
        return new CuratedItem(title, link, "中新网", "world", "摘要", 4, false, 1, "");
    }

    private DailyReport sampleReport() {
        // 非空报告：合并后非空才会出页（空精选会被短路）
        return new DailyReport("2026-05-30", "今日速览",
                List.of(curated("标题", "https://a.com/1")), 3, 7);
    }

    @Test
    void happyPath_callsAllStepsInOrderWithCorrectArguments() {
        List<NewsItem> raw = List.of(sampleItem());
        List<NewsItem> fresh = List.of(sampleItem());
        List<NewsItem> pool = List.of(sampleItem());
        DailyReport report = sampleReport();
        List<ReportMeta> recent = List.of(new ReportMeta("2026-05-30", 7, 3, "/news/2026-05-30.html"));
        String dailyHtml = "<html>daily</html>";
        String indexHtml = "<html>index</html>";

        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsConfig.getArchiveDays()).thenReturn(30);
        when(newsFetcher.fetchAll()).thenReturn(raw);
        when(newsStore.dedupAndSave(raw)).thenReturn(fresh);
        // Phase 2：精选输入是候选池而非 fresh
        when(newsStore.listEligibleForReport(anyString())).thenReturn(pool);
        when(newsAiCurator.curate(pool)).thenReturn(report);
        when(newsStore.listRecent(30)).thenReturn(recent);
        when(newsPageBuilder.buildDaily(report, recent)).thenReturn(dailyHtml);
        when(newsPageBuilder.buildArchiveIndex(recent)).thenReturn(indexHtml);
        when(newsHosting.publish("2026-05-30", dailyHtml, indexHtml)).thenReturn("/news/2026-05-30.html");

        schedule.runDaily();

        InOrder order = inOrder(newsFetcher, newsStore, newsAiCurator, newsPageBuilder, newsHosting);
        order.verify(newsFetcher).fetchAll();
        order.verify(newsStore).dedupAndSave(raw);
        order.verify(newsStore).listEligibleForReport(anyString());
        order.verify(newsAiCurator).curate(pool);
        order.verify(newsStore).saveReport(report);
        order.verify(newsStore).listRecent(30);
        order.verify(newsPageBuilder).buildDaily(report, recent);
        order.verify(newsPageBuilder).buildArchiveIndex(recent);
        order.verify(newsHosting).publish("2026-05-30", dailyHtml, indexHtml);

        // publish 收到 report.date() 与两个 html
        verify(newsHosting).publish(eq(report.date()), eq(dailyHtml), eq(indexHtml));
    }

    @Test
    void disabled_doesNothing() {
        when(newsConfig.isEnabled()).thenReturn(false);

        schedule.runDaily();

        verifyNoInteractions(newsFetcher, newsStore, newsAiCurator, newsPageBuilder, newsHosting);
    }

    @Test
    void fetchAllEmpty_shortCircuits() {
        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsFetcher.fetchAll()).thenReturn(Collections.emptyList());

        schedule.runDaily();

        verify(newsFetcher).fetchAll();
        verify(newsStore, never()).dedupAndSave(any());
        verify(newsAiCurator, never()).curate(any());
        verify(newsHosting, never()).publish(any(), any(), any());
    }

    @Test
    void dedupEmpty_shortCircuits() {
        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsFetcher.fetchAll()).thenReturn(List.of(sampleItem()));
        when(newsStore.dedupAndSave(any())).thenReturn(Collections.emptyList());

        schedule.runDaily();

        verify(newsStore).dedupAndSave(any());
        verify(newsAiCurator, never()).curate(any());
        verify(newsStore, never()).saveReport(any());
        verify(newsStore, never()).listRecent(anyInt());
        verify(newsHosting, never()).publish(any(), any(), any());
    }

    @Test
    void stepThrows_runDailySwallowsException() {
        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsFetcher.fetchAll()).thenThrow(new RuntimeException("boom"));

        // 定时入口必须吞异常
        assertThatNoException().isThrownBy(() -> schedule.runDaily());

        verify(newsHosting, never()).publish(any(), any(), any());
    }

    @Test
    void stepThrows_generateNowPropagates() {
        when(newsFetcher.fetchAll()).thenReturn(List.of(sampleItem()));
        when(newsStore.dedupAndSave(any())).thenReturn(List.of(sampleItem()));
        when(newsStore.listEligibleForReport(anyString())).thenReturn(List.of(sampleItem()));
        when(newsAiCurator.curate(any())).thenThrow(new RuntimeException("ai down"));

        // generateNow 本身不吞异常（手动触发可感知失败），仅 runDaily 兜底
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> schedule.generateNow());
    }

    // ---- Phase 1：同日合并不覆盖 / 空精选不灌水 / 生成互斥 ----

    @Test
    void sameDayRerun_mergesExistingSelected_savesUnion_notOverwrite() {
        List<NewsItem> fresh = List.of(sampleItem());
        // 本次精选只含 B
        DailyReport freshReport = new DailyReport("2026-05-30", "新速览",
                List.of(curated("B 新闻", "https://b.com/2")), 1, 1);
        // 当天已存在 A（第一次生成的精选）
        DailyReport existing = new DailyReport("2026-05-30", "旧速览",
                List.of(curated("A 新闻", "https://a.com/1")), 1, 1);

        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsConfig.getArchiveDays()).thenReturn(30);
        when(newsFetcher.fetchAll()).thenReturn(fresh);
        when(newsStore.dedupAndSave(fresh)).thenReturn(fresh);
        when(newsStore.listEligibleForReport(anyString())).thenReturn(fresh);
        when(newsAiCurator.curate(fresh)).thenReturn(freshReport);
        when(newsStore.getReport("2026-05-30")).thenReturn(existing);
        when(newsStore.listRecent(30)).thenReturn(Collections.emptyList());
        when(newsHosting.publish(eq("2026-05-30"), any(), any())).thenReturn("/news/2026-05-30.html");

        schedule.generateNow();

        ArgumentCaptor<DailyReport> saved = ArgumentCaptor.forClass(DailyReport.class);
        verify(newsStore).saveReport(saved.capture());
        // 合并后同时含 A 与 B：旧精选未被覆盖丢失
        assertThat(saved.getValue().items()).extracting(CuratedItem::title)
                .containsExactlyInAnyOrder("A 新闻", "B 新闻");
        verify(newsHosting).publish(eq("2026-05-30"), any(), any());
    }

    @Test
    void aiEmptySelection_withExisting_preservesExisting_andPublishes() {
        List<NewsItem> fresh = List.of(sampleItem());
        // 本次空精选（合法空，非降级灌水）
        DailyReport emptyReport = new DailyReport("2026-05-30", "今日暂无", Collections.emptyList(), 0, 0);
        DailyReport existing = new DailyReport("2026-05-30", "旧速览",
                List.of(curated("A 新闻", "https://a.com/1")), 1, 1);

        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsConfig.getArchiveDays()).thenReturn(30);
        when(newsFetcher.fetchAll()).thenReturn(fresh);
        when(newsStore.dedupAndSave(fresh)).thenReturn(fresh);
        when(newsStore.listEligibleForReport(anyString())).thenReturn(fresh);
        when(newsAiCurator.curate(fresh)).thenReturn(emptyReport);
        when(newsStore.getReport("2026-05-30")).thenReturn(existing);
        when(newsStore.listRecent(30)).thenReturn(Collections.emptyList());
        when(newsHosting.publish(eq("2026-05-30"), any(), any())).thenReturn("/news/2026-05-30.html");

        schedule.generateNow();

        ArgumentCaptor<DailyReport> saved = ArgumentCaptor.forClass(DailyReport.class);
        verify(newsStore).saveReport(saved.capture());
        // 空精选未抹掉既有 A
        assertThat(saved.getValue().items()).extracting(CuratedItem::title).containsExactly("A 新闻");
        verify(newsHosting).publish(eq("2026-05-30"), any(), any());
    }

    @Test
    void aiEmptySelection_noExisting_skipsPublish_noRawDump() {
        List<NewsItem> fresh = List.of(sampleItem());
        DailyReport emptyReport = new DailyReport("2026-05-30", "今日暂无", Collections.emptyList(), 0, 0);

        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsFetcher.fetchAll()).thenReturn(fresh);
        when(newsStore.dedupAndSave(fresh)).thenReturn(fresh);
        when(newsStore.listEligibleForReport(anyString())).thenReturn(fresh);
        when(newsAiCurator.curate(fresh)).thenReturn(emptyReport);
        when(newsStore.getReport("2026-05-30")).thenReturn(null);

        String url = schedule.generateNow();

        assertThat(url).isNull();
        verify(newsStore, never()).saveReport(any());
        verify(newsHosting, never()).publish(any(), any(), any());
    }

    @Test
    void concurrentGeneration_throwsBusy_whenAlreadyRunning() {
        // 模拟已有任务在执行：把互斥位置 true
        ((AtomicBoolean) ReflectionTestUtils.getField(schedule, "running")).set(true);

        assertThatThrownBy(() -> schedule.generateNow())
                .isInstanceOf(NewsGenerationBusyException.class);
        verifyNoInteractions(newsFetcher);
    }

    @Test
    void generateNow_releasesLock_afterException_allowingRetry() {
        when(newsFetcher.fetchAll())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Collections.emptyList());

        // 第一次失败后锁必须释放，第二次仍可进入（短路返回 null）
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> schedule.generateNow());
        assertThat(schedule.generateNow()).isNull();
    }

    @Test
    void mergeReports_existingNull_returnsFreshAsIs() {
        DailyReport fresh = new DailyReport("2026-05-30", "b",
                List.of(curated("B", "https://b/1")), 1, 1);
        assertThat(DailyNewsSchedule.mergeReports(null, fresh)).isSameAs(fresh);
    }

    @Test
    void mergeReports_sameLink_freshOverridesExisting() {
        DailyReport existing = new DailyReport("2026-05-30", "old",
                List.of(curated("旧标题", "https://x/1")), 1, 1);
        DailyReport fresh = new DailyReport("2026-05-30", "new",
                List.of(curated("新标题", "https://x/1")), 1, 1);

        DailyReport merged = DailyNewsSchedule.mergeReports(existing, fresh);

        // 同链接以本次为准，不重复
        assertThat(merged.items()).hasSize(1);
        assertThat(merged.items().get(0).title()).isEqualTo("新标题");
        assertThat(merged.brief()).isEqualTo("new");
    }
}
