package com.bb.bot.controller;

import com.bb.bot.config.NewsConfig;
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
 * 以及成功 / 短路 / 异常的返回。
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

    @Test
    void run_success_returnsUrl() throws Exception {
        when(schedule.generateNow()).thenReturn("/news/2026-05-30.html");

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/news/2026-05-30.html")));
    }

    @Test
    void run_success_acceptsTokenFromHeader() throws Exception {
        when(schedule.generateNow()).thenReturn("/news/x.html");

        mockMvc.perform(post("/news/run").header("X-News-Token", TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void run_shortCircuit_returnsOkWithHint() throws Exception {
        when(schedule.generateNow()).thenReturn(null);

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("未出页")));
    }

    @Test
    void run_exception_returns500() throws Exception {
        doThrow(new RuntimeException("db down")).when(schedule).generateNow();

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("db down")));
    }

    @Test
    void run_noToken_returns403_andNeverTriggers() throws Exception {
        mockMvc.perform(post("/news/run"))
                .andExpect(status().isForbidden());
        verify(schedule, never()).generateNow();
    }

    @Test
    void run_wrongToken_returns403() throws Exception {
        mockMvc.perform(post("/news/run").param("token", "wrong"))
                .andExpect(status().isForbidden());
        verify(schedule, never()).generateNow();
    }

    @Test
    void run_tokenNotConfigured_failsClosed_returns403() throws Exception {
        newsConfig.getAdmin().setToken("");

        mockMvc.perform(post("/news/run").param("token", "anything"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("已禁用")));
        verify(schedule, never()).generateNow();
    }

    @Test
    void run_busy_returns409() throws Exception {
        doThrow(new NewsGenerationBusyException("已有日报生成任务在执行，请稍后再试"))
                .when(schedule).generateNow();

        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("执行")));
    }

    @Test
    void run_rateLimited_secondCallWithinWindowReturns429() throws Exception {
        when(schedule.generateNow()).thenReturn("/news/x.html");

        // 第一次通过
        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isOk());
        // 紧接着第二次：默认 10 分钟窗口内 → 429
        mockMvc.perform(post("/news/run").param("token", TOKEN))
                .andExpect(status().isTooManyRequests());
    }
}
