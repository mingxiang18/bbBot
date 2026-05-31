package com.bb.bot.controller;

import com.bb.bot.schedule.DailyNewsSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link NewsAdminController} 单测：手动触发端点的成功 / 短路 / 异常三种返回。
 */
class NewsAdminControllerTest {

    private DailyNewsSchedule schedule;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        schedule = mock(DailyNewsSchedule.class);
        NewsAdminController controller = new NewsAdminController();
        ReflectionTestUtils.setField(controller, "dailyNewsSchedule", schedule);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void run_success_returnsUrl() throws Exception {
        when(schedule.generateNow()).thenReturn("/news/2026-05-30.html");

        mockMvc.perform(post("/news/run"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/news/2026-05-30.html")));
    }

    @Test
    void run_shortCircuit_returnsOkWithHint() throws Exception {
        when(schedule.generateNow()).thenReturn(null);

        mockMvc.perform(post("/news/run"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("未出页")));
    }

    @Test
    void run_exception_returns500() throws Exception {
        doThrow(new RuntimeException("db down")).when(schedule).generateNow();

        mockMvc.perform(post("/news/run"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("db down")));
    }
}
