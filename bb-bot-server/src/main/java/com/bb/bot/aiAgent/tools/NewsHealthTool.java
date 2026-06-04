package com.bb.bot.aiAgent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.database.news.entity.NewsRunStatsPo;
import com.bb.bot.database.news.entity.NewsSourceHealthPo;
import com.bb.bot.database.news.service.INewsRunStatsService;
import com.bb.bot.database.news.service.INewsSourceHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新闻日报健康自查工具（owner 专用）：回答「新闻源有没有失效 / 今天筛了几条 / AI 是否降级」。
 *
 * <p>数据来自 P2 落库的 {@code news_source_health} 与 {@code news_run_stats}。各源取最近一次抓取
 * 状态判定健康度（ok=正常 / empty=疑似停更 / 其它=失败），并附最近几次日报生成指标。</p>
 */
@Slf4j
@Component
public class NewsHealthTool {

    @Autowired(required = false)
    private INewsSourceHealthService sourceHealthService;

    @Autowired(required = false)
    private INewsRunStatsService runStatsService;

    @AiTool(
            name = "news_health",
            description = "自查每日资讯日报的健康状况：各新闻源最近一次抓取是否成功、哪些源失效/停更，"
                    + "以及最近几次日报生成的指标（采集/新增/候选/精选条数、AI 是否降级、是否出页）。"
                    + "当 owner 问「新闻源有没有失效 / 今天的新闻是怎么筛的 / 日报怎么没更新 / 优质源还在吗」时调用。",
            requiresOwner = true)
    public Map<String, Object> newsHealth(
            @AiToolParam(name = "hours", description = "源健康统计回溯小时数，默认 48", required = false)
            Integer hours) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sourceHealthService == null || runStatsService == null) {
            result.put("error", "unavailable");
            result.put("message", "新闻健康数据服务未就绪");
            return result;
        }
        int window = hours == null || hours <= 0 ? 48 : hours;
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(window);
            // 回溯窗口内的源健康记录，按时间倒序 → 每源取首条即最近一次
            List<NewsSourceHealthPo> rows = sourceHealthService.list(
                    new LambdaQueryWrapper<NewsSourceHealthPo>()
                            .ge(NewsSourceHealthPo::getCheckedAt, since)
                            .orderByDesc(NewsSourceHealthPo::getCheckedAt));

            Map<String, NewsSourceHealthPo> latestBySource = new LinkedHashMap<>();
            Map<String, Integer> failCount = new LinkedHashMap<>();
            for (NewsSourceHealthPo r : rows) {
                latestBySource.putIfAbsent(r.getSourceName(), r);
                if (!"ok".equals(r.getStatus())) {
                    failCount.merge(r.getSourceName(), 1, Integer::sum);
                }
            }

            List<Map<String, Object>> sources = new ArrayList<>();
            List<String> failing = new ArrayList<>();
            int healthy = 0, stale = 0, failed = 0;
            for (NewsSourceHealthPo r : latestBySource.values()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("source", r.getSourceName());
                s.put("status", r.getStatus());
                s.put("itemCount", r.getItemCount());
                s.put("errorType", r.getErrorType());
                s.put("lastCheckedAt", r.getCheckedAt() == null ? null : r.getCheckedAt().toString());
                s.put("failsInWindow", failCount.getOrDefault(r.getSourceName(), 0));
                sources.add(s);
                if ("ok".equals(r.getStatus())) {
                    healthy++;
                } else if ("empty".equals(r.getStatus())) {
                    stale++;
                    failing.add(r.getSourceName());
                } else {
                    failed++;
                    failing.add(r.getSourceName());
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("windowHours", window);
            summary.put("sourcesSeen", latestBySource.size());
            summary.put("healthy", healthy);
            summary.put("stale", stale);
            summary.put("failed", failed);
            result.put("summary", summary);
            result.put("failingSources", failing);
            result.put("sources", sources);

            // 最近 7 次日报生成指标
            List<NewsRunStatsPo> runs = runStatsService.list(
                    new LambdaQueryWrapper<NewsRunStatsPo>()
                            .orderByDesc(NewsRunStatsPo::getId)
                            .last("limit 7"));
            List<Map<String, Object>> recentRuns = new ArrayList<>();
            for (NewsRunStatsPo run : runs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("reportDate", run.getReportDate() == null ? null : run.getReportDate().toString());
                m.put("fetched", run.getFetched());
                m.put("fresh", run.getFresh());
                m.put("eligible", run.getEligible());
                m.put("selected", run.getSelected());
                m.put("aiStatus", run.getAiStatus());
                m.put("published", run.getPublished());
                m.put("url", run.getUrl());
                m.put("at", run.getCreatedAt() == null ? null : run.getCreatedAt().toString());
                recentRuns.add(m);
            }
            result.put("recentRuns", recentRuns);

            if (latestBySource.isEmpty() && recentRuns.isEmpty()) {
                result.put("hint", "近 " + window + " 小时无抓取/生成记录（可能日报功能未运行或刚上线尚无数据）");
            }
            return result;
        } catch (Exception e) {
            log.warn("news_health 失败", e);
            result.put("error", "news_health_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
