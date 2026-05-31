package com.bb.bot.aiAgent.tools;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.handler.news.contract.ReportMeta;
import com.bb.bot.schedule.DailyNewsSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DailyNewsTool} 单测：today / generate / archive 三个工具的存在判断、触发与降级返回。
 */
class DailyNewsToolTest {

    private NewsStore newsStore;
    private DailyNewsSchedule schedule;
    private NewsConfig newsConfig;
    private DailyNewsTool tool;

    @BeforeEach
    void setUp() {
        newsStore = mock(NewsStore.class);
        schedule = mock(DailyNewsSchedule.class);
        newsConfig = new NewsConfig();
        newsConfig.getHosting().setPublicBase("/news");
        newsConfig.getHosting().setExternalBaseUrl("http://host:8099");

        tool = new DailyNewsTool();
        ReflectionTestUtils.setField(tool, "newsStore", newsStore);
        ReflectionTestUtils.setField(tool, "dailyNewsSchedule", schedule);
        ReflectionTestUtils.setField(tool, "newsConfig", newsConfig);
    }

    private DailyReport report(int count) {
        return new DailyReport(LocalDate.now().toString(), "今日速览", Collections.emptyList(), 5, count);
    }

    @Test
    void today_whenExists_returnsUrlAndCount() {
        when(newsStore.getReport(LocalDate.now().toString())).thenReturn(report(12));

        Map<String, Object> r = tool.today();

        assertThat(r.get("exists")).isEqualTo(true);
        assertThat(r.get("count")).isEqualTo(12);
        assertThat(r.get("url")).isEqualTo("http://host:8099/news/" + LocalDate.now() + ".html");
    }

    @Test
    void today_whenAbsent_returnsExistsFalse_andDoesNotGenerate() {
        when(newsStore.getReport(LocalDate.now().toString())).thenReturn(null);

        Map<String, Object> r = tool.today();

        assertThat(r.get("exists")).isEqualTo(false);
        verify(schedule, never()).generateNow();
    }

    @Test
    void generate_success_returnsUrlAndCount() {
        when(schedule.generateNow()).thenReturn("/news/x.html");
        when(newsStore.getReport(LocalDate.now().toString())).thenReturn(report(8));

        Map<String, Object> r = tool.generate();

        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("count")).isEqualTo(8);
        assertThat(r.get("url")).isEqualTo("http://host:8099/news/" + LocalDate.now() + ".html");
    }

    @Test
    void generate_whenNoNews_returnsNotOk() {
        when(schedule.generateNow()).thenReturn(null);

        Map<String, Object> r = tool.generate();

        assertThat(r.get("ok")).isEqualTo(false);
        assertThat(r.get("error")).isEqualTo("no_news");
    }

    @Test
    void archive_mapsMetasToFullUrls() {
        when(newsStore.listRecent(anyInt())).thenReturn(List.of(
                new ReportMeta("2026-05-30", 10, 5, "/news/2026-05-30.html"),
                new ReportMeta("2026-05-29", 9, 4, "/news/2026-05-29.html")
        ));

        Map<String, Object> r = tool.archive(7);

        assertThat(r.get("ok")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) r.get("archives");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("url")).isEqualTo("http://host:8099/news/2026-05-30.html");
    }
}
