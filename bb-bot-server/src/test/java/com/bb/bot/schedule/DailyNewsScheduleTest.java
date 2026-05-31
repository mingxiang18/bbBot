package com.bb.bot.schedule;

import com.bb.bot.config.NewsConfig;
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
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    private DailyReport sampleReport() {
        return new DailyReport("2026-05-30", "今日速览", Collections.emptyList(), 3, 7);
    }

    @Test
    void happyPath_callsAllStepsInOrderWithCorrectArguments() {
        List<NewsItem> raw = List.of(sampleItem());
        List<NewsItem> fresh = List.of(sampleItem());
        DailyReport report = sampleReport();
        List<ReportMeta> recent = List.of(new ReportMeta("2026-05-30", 7, 3, "/news/2026-05-30.html"));
        String dailyHtml = "<html>daily</html>";
        String indexHtml = "<html>index</html>";

        when(newsConfig.isEnabled()).thenReturn(true);
        when(newsConfig.getArchiveDays()).thenReturn(30);
        when(newsFetcher.fetchAll()).thenReturn(raw);
        when(newsStore.dedupAndSave(raw)).thenReturn(fresh);
        when(newsAiCurator.curate(fresh)).thenReturn(report);
        when(newsStore.listRecent(30)).thenReturn(recent);
        when(newsPageBuilder.buildDaily(report, recent)).thenReturn(dailyHtml);
        when(newsPageBuilder.buildArchiveIndex(recent)).thenReturn(indexHtml);
        when(newsHosting.publish("2026-05-30", dailyHtml, indexHtml)).thenReturn("/news/2026-05-30.html");

        schedule.runDaily();

        InOrder order = inOrder(newsFetcher, newsStore, newsAiCurator, newsPageBuilder, newsHosting);
        order.verify(newsFetcher).fetchAll();
        order.verify(newsStore).dedupAndSave(raw);
        order.verify(newsAiCurator).curate(fresh);
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
        when(newsAiCurator.curate(any())).thenThrow(new RuntimeException("ai down"));

        // generateNow 本身不吞异常（手动触发可感知失败），仅 runDaily 兜底
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> schedule.generateNow());
    }
}
