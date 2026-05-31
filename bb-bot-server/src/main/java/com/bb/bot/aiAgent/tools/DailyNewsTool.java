package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.NewsUrls;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.handler.news.contract.ReportMeta;
import com.bb.bot.schedule.DailyNewsSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日资讯日报的 AI Agent 工具。
 *
 * <p>典型用法（由 LLM 编排）：用户想看今日资讯 → 先调 {@code daily_news_today} 判断是否已生成 →
 * 若不存在再调 {@code daily_news_generate} 触发生成 → 把返回的链接告诉用户。</p>
 */
@Slf4j
@Component
public class DailyNewsTool {

    @Autowired
    private NewsStore newsStore;

    @Autowired
    private DailyNewsSchedule dailyNewsSchedule;

    @Autowired
    private NewsConfig newsConfig;

    @AiTool(
            name = "daily_news_today",
            description = "查询今天的每日资讯日报是否已生成。返回 exists 字段；若已生成，附带访问链接 url、"
                    + "精选条数 count 与速览 brief。用户想看今日资讯/日报时，先调本工具判断，再决定是否生成。"
    )
    public Map<String, Object> today() {
        Map<String, Object> r = new LinkedHashMap<>();
        String today = LocalDate.now().toString();
        try {
            DailyReport report = newsStore.getReport(today);
            r.put("date", today);
            if (report == null) {
                r.put("exists", false);
                r.put("hint", "今日日报尚未生成，可调用 daily_news_generate 生成");
                return r;
            }
            r.put("exists", true);
            r.put("url", NewsUrls.fullFor(newsConfig, today));
            r.put("count", report.totalCount());
            r.put("brief", report.brief());
            return r;
        } catch (Exception e) {
            log.warn("[news] daily_news_today 失败", e);
            r.put("error", "query_failed");
            r.put("message", e.getMessage());
            return r;
        }
    }

    @AiTool(
            name = "daily_news_generate",
            description = "立即生成（或重建）今天的每日资讯日报：采集权威源→AI整理→生成可交互网页。"
                    + "当今日日报尚不存在、或用户明确要求生成/刷新时调用。返回访问链接 url 与精选条数 count。"
    )
    public Map<String, Object> generate() {
        Map<String, Object> r = new LinkedHashMap<>();
        String today = LocalDate.now().toString();
        try {
            String url = dailyNewsSchedule.generateNow();
            if (url == null) {
                r.put("ok", false);
                r.put("error", "no_news");
                r.put("hint", "今日暂无新资讯，未出页");
                return r;
            }
            DailyReport report = newsStore.getReport(today);
            r.put("ok", true);
            r.put("date", today);
            r.put("url", NewsUrls.fullFor(newsConfig, today));
            r.put("count", report == null ? 0 : report.totalCount());
            if (report != null) {
                r.put("brief", report.brief());
            }
            return r;
        } catch (Exception e) {
            log.warn("[news] daily_news_generate 失败", e);
            r.put("ok", false);
            r.put("error", "generation_failed");
            r.put("message", e.getMessage());
            return r;
        }
    }

    @AiTool(
            name = "daily_news_archive",
            description = "列出最近几天已生成的每日资讯日报归档，每项含日期、精选条数与访问链接。"
    )
    public Map<String, Object> archive(
            @AiToolParam(name = "days", description = "查询最近 N 天，默认 7，最大 30", required = false)
            Integer days
    ) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            int n = days == null ? 7 : Math.min(Math.max(days, 1), 30);
            List<ReportMeta> metas = newsStore.listRecent(n);
            List<Map<String, Object>> list = new ArrayList<>();
            for (ReportMeta m : metas) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", m.date());
                item.put("count", m.totalCount());
                item.put("url", NewsUrls.fullFor(newsConfig, m.date()));
                list.add(item);
            }
            r.put("ok", true);
            r.put("archives", list);
            return r;
        } catch (Exception e) {
            log.warn("[news] daily_news_archive 失败", e);
            r.put("error", "query_failed");
            r.put("message", e.getMessage());
            return r;
        }
    }
}
