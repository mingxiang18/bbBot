package com.bb.bot.schedule;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.LinkHash;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /** 生成互斥：定时与手动触发共享，避免并发生成相互覆盖。 */
    private final AtomicBoolean running = new AtomicBoolean(false);

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
     * 承载日报生成主体，便于单测与手动触发。
     *
     * <p>注意：本方法不做 enabled 判断（由 {@link #runDaily()} 负责），也不吞异常——
     * 手动触发场景下调用方可直接感知失败。定时场景的吞异常由 {@link #runDaily()} 兜底。</p>
     *
     * @return 生成成功时返回当日页访问 URL；无采集内容/无新增条目/空精选而短路时返回 null
     * @throws NewsGenerationBusyException 已有生成任务在执行
     */
    public String generateNow() {
        if (!running.compareAndSet(false, true)) {
            throw new NewsGenerationBusyException("已有日报生成任务在执行，请稍后再试");
        }
        try {
            return doGenerate();
        } finally {
            running.set(false);
        }
    }

    private String doGenerate() {
        List<NewsItem> items = newsFetcher.fetchAll();
        if (items == null || items.isEmpty()) {
            log.info("本次未采集到任何资讯条目，跳过出页");
            return null;
        }

        List<NewsItem> fresh = newsStore.dedupAndSave(items);
        if (fresh == null || fresh.isEmpty()) {
            log.info("去重后无新增条目（共采集 {} 条），保留既有日报，跳过出页", items.size());
            return null;
        }

        DailyReport freshReport = newsAiCurator.curate(fresh);

        // 同日合并：把当天已选条目并入本次结果，避免二次生成只用新增条目覆盖旧页而丢失已选内容。
        DailyReport existing = newsStore.getReport(freshReport.date());
        DailyReport report = mergeReports(existing, freshReport);

        if (report.items() == null || report.items().isEmpty()) {
            // 宁缺毋滥：本次空精选且无既有内容 → 不出页、不覆盖（避免 raw 灌水）
            log.info("精选结果为空且无既有日报，跳过出页（日期={}）", report.date());
            return null;
        }

        newsStore.saveReport(report);

        List<ReportMeta> availableDates = newsStore.listRecent(newsConfig.getArchiveDays());

        String dailyHtml = newsPageBuilder.buildDaily(report, availableDates);
        String indexHtml = newsPageBuilder.buildArchiveIndex(availableDates);

        String url = newsHosting.publish(report.date(), dailyHtml, indexHtml);

        // 归档保留裁剪：删除超出保留期的历史记录
        newsStore.pruneOld(newsConfig.getArchiveDays());

        log.info("每日资讯日报生成成功，日期={}，精选 {} 条，访问地址={}",
                report.date(), report.totalCount(), url);
        return url;
    }

    /**
     * 合并既有日报与本次结果：按链接归一化哈希 union，本次结果覆盖同链接的旧条目（取较新 AI 摘要），
     * 既有独有条目得以保留。brief 优先取本次非空值，否则沿用既有。
     *
     * <p>语义保证："同日二次生成不会删除第一次精选的内容"；当本次为空精选时，等价于保留旧版本。</p>
     *
     * @param existing 既有日报，可为 null
     * @param fresh    本次生成结果，非 null
     * @return 合并后的日报；既有为空时直接返回 {@code fresh}
     */
    static DailyReport mergeReports(DailyReport existing, DailyReport fresh) {
        if (existing == null || existing.items() == null || existing.items().isEmpty()) {
            return fresh;
        }
        LinkedHashMap<String, CuratedItem> byHash = new LinkedHashMap<>();
        for (CuratedItem it : existing.items()) {
            String h = LinkHash.of(it.link());
            if (h != null) {
                byHash.put(h, it);
            }
        }
        if (fresh.items() != null) {
            for (CuratedItem it : fresh.items()) {
                String h = LinkHash.of(it.link());
                if (h != null) {
                    byHash.put(h, it); // 同链接以本次为准
                }
            }
        }
        List<CuratedItem> merged = new ArrayList<>(byHash.values());
        String brief = fresh.brief() != null && !fresh.brief().isBlank()
                ? fresh.brief() : existing.brief();
        long sources = merged.stream()
                .map(CuratedItem::sourceName)
                .filter(s -> s != null)
                .distinct()
                .count();
        return new DailyReport(fresh.date(), brief, merged, (int) sources, merged.size());
    }
}
