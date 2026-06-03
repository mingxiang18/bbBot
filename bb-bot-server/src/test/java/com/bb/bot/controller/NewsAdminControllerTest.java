package com.bb.bot.controller;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.NewsRunStats;
import com.bb.bot.schedule.DailyNewsSchedule;
import com.bb.bot.schedule.NewsGenerationBusyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link NewsAdminController} 单测：鉴权（令牌 / fail-closed）、限流、互斥（409）、
 * dryRun，以及成功 / 短路 / 异常的返回。
 */
class NewsAdminControllerTest {

    private static final String TOKEN = "s3cret";

    private DailyNewsSchedule schedule;
    private NewsConfig newsConfig;
    private NewsAdminController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        schedule = mock(DailyNewsSchedule.class);
        newsConfig = new NewsConfig();
        newsConfig.getAdmin().setToken(TOKEN);
        controller = new NewsAdminController();
        ReflectionTestUtils.setField(controller, "dailyNewsSchedule", schedule);
        ReflectionTestUtils.setField(controller, "newsConfig", newsConfig);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static NewsRunStats published(String url) {
        return new NewsRunStats(20, 5, 18, 12, "success", true, url);
    }

    private static NewsRunStats notPublished() {
        return new NewsRunStats(20, 0, 0, 0, "no_candidate", false, null);
    }

    @Test
    void run_success_returnsUrl() throws Exception {
        when(schedule.generateNow(false, false)).thenReturn(published("/news/2026-05-30.html"));

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/news/2026-05-30.html")));
    }

    @Test
    void run_success_acceptsTokenFromHeader() throws Exception {
        when(schedule.generateNow(false, false)).thenReturn(published("/news/x.html"));

        mockMvc.perform(post("/news/run").header("X-News-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void run_shortCircuit_returnsOkWithHint() throws Exception {
        when(schedule.generateNow(false, false)).thenReturn(notPublished());

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("未出页")));
    }

    @Test
    void run_dryRun_returnsStatsWithoutPublishing() throws Exception {
        when(schedule.generateNow(true, false))
                .thenReturn(new NewsRunStats(20, 5, 18, 12, "success", false, null));

        mockMvc.perform(post("/news/run").param("token", TOKEN).param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dryRun")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("selected=12")));
        verify(schedule).generateNow(true, false);
    }

    @Test
    void run_exception_returns500() throws Exception {
        doThrow(new RuntimeException("db down")).when(schedule).generateNow(false, false);

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("db down")));
    }

    @Test
    void run_noToken_returns403_andNeverTriggers() throws Exception {
        mockMvc.perform(post("/news/run"))
                .andExpect(status().isForbidden());
        verify(schedule, never()).generateNow(false, false);
    }

    @Test
    void run_wrongToken_returns403() throws Exception {
        mockMvc.perform(post("/news/run").param("token", "wrong"))
                .andExpect(status().isForbidden());
        verify(schedule, never()).generateNow(false, false);
    }

    @Test
    void run_tokenNotConfigured_failsClosed_returns403() throws Exception {
        newsConfig.getAdmin().setToken("");

        mockMvc.perform(post("/news/run").param("token", "anything"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("已禁用")));
        verify(schedule, never()).generateNow(false, false);
    }

    @Test
    void run_busy_returns409() throws Exception {
        doThrow(new NewsGenerationBusyException("已有日报生成任务在执行，请稍后再试"))
                .when(schedule).generateNow(false, false);

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("执行")));
    }

    @Test
    void run_rateLimited_secondCallWithinWindowReturns429() throws Exception {
        when(schedule.generateNow(false, false)).thenReturn(published("/news/x.html"));

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isTooManyRequests());
    }
}
