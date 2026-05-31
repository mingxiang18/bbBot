package com.bb.bot.handler.news;

import com.bb.bot.common.util.BbReplies;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.schedule.DailyNewsSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DailyNewsHandler} 命令单测：已存在直接回链接 / 不存在先生成再回 / 异常回错误。
 */
class DailyNewsHandlerTest {

    private BbReplies bbReplies;
    private NewsStore newsStore;
    private DailyNewsSchedule schedule;
    private NewsConfig newsConfig;
    private DailyNewsHandler handler;

    @BeforeEach
    void setUp() {
        bbReplies = mock(BbReplies.class);
        newsStore = mock(NewsStore.class);
        schedule = mock(DailyNewsSchedule.class);
        newsConfig = new NewsConfig();
        newsConfig.getHosting().setPublicBase("/news");
        newsConfig.getHosting().setExternalBaseUrl("http://host:8099");

        handler = new DailyNewsHandler();
        ReflectionTestUtils.setField(handler, "bbReplies", bbReplies);
        ReflectionTestUtils.setField(handler, "newsStore", newsStore);
        ReflectionTestUtils.setField(handler, "dailyNewsSchedule", schedule);
        ReflectionTestUtils.setField(handler, "newsConfig", newsConfig);
    }

    private BbReceiveMessage msg() {
        BbReceiveMessage m = new BbReceiveMessage();
        m.setUserId("u1");
        m.setGroupId("g1");
        return m;
    }

    private DailyReport report(int count) {
        return new DailyReport(LocalDate.now().toString(), "速览", Collections.emptyList(), 5, count);
    }

    @Test
    void existing_repliesLinkWithoutGenerating() {
        when(newsStore.getReport(LocalDate.now().toString())).thenReturn(report(12));

        handler.daily(msg());

        verify(schedule, never()).generateNow();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bbReplies, atLeastOnce()).atText(org.mockito.ArgumentMatchers.any(), text.capture());
        assertThat(text.getValue()).contains("http://host:8099/news/").contains("12");
    }

    @Test
    void absent_generatesThenRepliesLink() {
        String today = LocalDate.now().toString();
        when(newsStore.getReport(today)).thenReturn(null, report(7));
        when(schedule.generateNow()).thenReturn("/news/x.html");

        handler.daily(msg());

        verify(schedule, times(1)).generateNow();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bbReplies, atLeastOnce()).atText(org.mockito.ArgumentMatchers.any(), text.capture());
        assertThat(text.getAllValues()).anySatisfy(s ->
                assertThat(s).contains("http://host:8099/news/").contains("7"));
    }

    @Test
    void absent_noNews_repliesHint() {
        when(newsStore.getReport(LocalDate.now().toString())).thenReturn(null);
        when(schedule.generateNow()).thenReturn(null);

        handler.daily(msg());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bbReplies, atLeastOnce()).atText(org.mockito.ArgumentMatchers.any(), text.capture());
        assertThat(text.getAllValues()).anySatisfy(s -> assertThat(s).contains("暂无新资讯"));
    }
}
