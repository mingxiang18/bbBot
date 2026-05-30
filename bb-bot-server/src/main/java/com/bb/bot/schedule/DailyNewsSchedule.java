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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每日资讯日报调度编排（任务 T6）。
 *
 * <p>本类只做编排，不含任何业务逻辑：依赖 5 个 contract 接口（采集 / 持久化 / AI 整理 /
 * 页面生成 / 托管），按固定顺序串联出每日日报流水线。各步骤的具体实现由对应模块负责。</p>
 *
 * <p>定时任务的容错原则：单次执行的异常必须被吞掉（记 error 日志），不得上抛，
 * 以免一次失败导致整个调度停摆。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class DailyNewsSchedule {

    @Autowired
    private NewsConfig newsConfig;

    @Autowired
    private NewsFetcher newsFetcher;

    @Autowired
    private NewsStore newsStore;

    @Autowired
    private NewsAiCurator newsAiCurator;

    @Autowired
    private NewsPageBuilder newsPageBuilder;

    @Autowired
    private NewsHosting newsHosting;

    /**
     * 每日定时触发（默认 08:00）。整个流程包一层 try-catch，异常只记日志不上抛。
     */
    @Scheduled(cron = "${news.cron:0 0 8 * * ?}")
    public void runDaily() {
        if (!newsConfig.isEnabled()) {
            log.info("每日资讯日报功能已关闭（news.enabled=false），跳过本次执行");
            return;
        }

        try {
            generateNow();
        } catch (Exception e) {
            // 定时任务不能因异常停摆：吞掉异常，仅记录
            log.error("每日资讯日报生成失败", e);
        }
    }

    /**
     * 承载日报生成主体，便于单测与将来手动触发。
     *
     * <p>注意：本方法不做 enabled 判断（由 {@link #runDaily()} 负责），也不吞异常——
     * 手动触发场景下调用方可直接感知失败。定时场景的吞异常由 {@link #runDaily()} 兜底。</p>
     */
    public void generateNow() {
        List<NewsItem> items = newsFetcher.fetchAll();
        if (items == null || items.isEmpty()) {
            log.info("本次未采集到任何资讯条目，跳过出页");
            return;
        }

        List<NewsItem> fresh = newsStore.dedupAndSave(items);
        if (fresh == null || fresh.isEmpty()) {
            log.info("去重后无新增条目（共采集 {} 条），跳过出页", items.size());
            return;
        }

        DailyReport report = newsAiCurator.curate(fresh);
        newsStore.saveReport(report);

        List<ReportMeta> availableDates = newsStore.listRecent(newsConfig.getArchiveDays());

        String dailyHtml = newsPageBuilder.buildDaily(report, availableDates);
        String indexHtml = newsPageBuilder.buildArchiveIndex(availableDates);

        String url = newsHosting.publish(report.date(), dailyHtml, indexHtml);

        // 归档保留裁剪：删除超出保留期的历史记录
        newsStore.pruneOld(newsConfig.getArchiveDays());

        log.info("每日资讯日报生成成功，日期={}，精选 {} 条，访问地址={}",
                report.date(), report.totalCount(), url);
    }
}
