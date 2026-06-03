package com.bb.bot.handler.news.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.config.NewsConfig;
import com.bb.bot.database.news.entity.NewsDailyPo;
import com.bb.bot.database.news.entity.NewsItemPo;
import com.bb.bot.database.news.service.INewsDailyService;
import com.bb.bot.database.news.service.INewsItemService;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.LinkHash;
import com.bb.bot.handler.news.contract.NewsDateParser;
import com.bb.bot.handler.news.contract.NewsItem;
import com.bb.bot.handler.news.contract.NewsReviewState;
import com.bb.bot.handler.news.contract.NewsStore;
import com.bb.bot.handler.news.contract.ReportMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资讯持久化与去重实现（任务 T1）。
 *
 * <p>仅负责 L1 物理去重（按 {@link NewsItem#linkHash} 同 link 去重）、原始/整理结果持久化、
 * 归档查询与保留裁剪。语义级聚类（L2）不在此处。</p>
 */
@Component
public class NewsStoreImpl implements NewsStore {

    @Autowired
    private INewsItemService newsItemService;

    @Autowired
    private INewsDailyService newsDailyService;

    @Autowired
    private NewsConfig newsConfig;

    /**
     * 采集 upsert（Phase 2）：新条目以 {@code review_state=RAW} 入库并记录 first/last_seen_at 与
     * published_at；已存在条目只刷新 {@code last_seen_at}（表示仍在源 feed 中出现），<b>不</b>因
     * 一次入库就把它判死。返回本次真正新增的条目。
     */
    @Override
    public List<NewsItem> dedupAndSave(List<NewsItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        // 收集本批所有 linkHash
        Set<String> hashes = new HashSet<>();
        for (NewsItem item : items) {
            if (StringUtils.hasText(item.linkHash())) {
                hashes.add(item.linkHash());
            }
        }

        // 查出已存在的 linkHash（用惰性 .in 条件，读全行取 linkHash；避免 .select(SFunction) 的立即列解析）
        Set<String> existing = new HashSet<>();
        if (!hashes.isEmpty()) {
            LambdaQueryWrapper<NewsItemPo> query = new LambdaQueryWrapper<>();
            query.in(NewsItemPo::getLinkHash, hashes);
            for (NewsItemPo po : newsItemService.list(query)) {
                existing.add(po.getLinkHash());
            }
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 已存在条目：批量刷新 last_seen_at（一条 UPDATE ... WHERE link_hash IN (...)）。
        // 用 update(entity, 惰性 wrapper) 而非 LambdaUpdateWrapper.set(SFunction)——后者会立即解析
        // 列名走 lambda 缓存，无 MyBatis 上下文的单测里会抛 "can not find lambda cache"。
        if (!existing.isEmpty()) {
            NewsItemPo touch = new NewsItemPo();
            touch.setLastSeenAt(now);
            LambdaQueryWrapper<NewsItemPo> where = new LambdaQueryWrapper<>();
            where.in(NewsItemPo::getLinkHash, existing);
            newsItemService.update(touch, where);
        }

        // 批内同 hash 也去重，避免一批里重复 link 重复插入
        Set<String> seen = new HashSet<>(existing);
        List<NewsItem> fresh = new ArrayList<>();
        List<NewsItemPo> toInsert = new ArrayList<>();
        for (NewsItem item : items) {
            String hash = item.linkHash();
            if (!StringUtils.hasText(hash) || seen.contains(hash)) {
                continue;
            }
            seen.add(hash);
            fresh.add(item);
            toInsert.add(toRawPo(item, today, now));
        }

        if (!toInsert.isEmpty()) {
            newsItemService.saveBatch(toInsert);
        }
        return fresh;
    }

    /**
     * 候选池：时间窗内的 {@code RAW} 条目（含 review_state 为空的历史行），按源级 windowHours 二次过滤。
     */
    @Override
    public List<NewsItem> listEligibleForReport(String date) {
        int globalWindow = Math.max(1, newsConfig.getCandidateWindowHours());
        int maxWindow = globalWindow;
        Map<String, Integer> sourceWindow = new java.util.HashMap<>();
        if (newsConfig.getSources() != null) {
            for (NewsConfig.Source s : newsConfig.getSources()) {
                int w = s.getWindowHours() > 0 ? s.getWindowHours() : globalWindow;
                sourceWindow.put(s.getName(), w);
                maxWindow = Math.max(maxWindow, w);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime maxCutoff = now.minusHours(maxWindow);

        LambdaQueryWrapper<NewsItemPo> query = new LambdaQueryWrapper<>();
        query.and(w -> w.eq(NewsItemPo::getReviewState, NewsReviewState.RAW)
                        .or().isNull(NewsItemPo::getReviewState))
                .and(w -> w.ge(NewsItemPo::getFirstSeenAt, maxCutoff)
                        .or().isNull(NewsItemPo::getFirstSeenAt))
                .orderByDesc(NewsItemPo::getFirstSeenAt);

        List<NewsItem> pool = new ArrayList<>();
        for (NewsItemPo po : newsItemService.list(query)) {
            // 源级时间窗二次过滤（不同源 cutoff 不同，SQL 用最宽窗，这里收紧）
            Integer w = sourceWindow.get(po.getSourceName());
            if (w != null && po.getFirstSeenAt() != null
                    && po.getFirstSeenAt().isBefore(now.minusHours(w))) {
                continue;
            }
            pool.add(new NewsItem(
                    po.getSourceName(), po.getCategory(), po.getTitle(), po.getLink(),
                    po.getDescription(), po.getPubDate(), po.getLang(), po.getLinkHash()));
        }
        return pool;
    }

    /**
     * 持久化某天整理结果：upsert news_daily（html_path 留空），
     * 并把每个 CuratedItem 的 AI 字段按 link 的 hash 回写到当天的 news_item 行。
     */
    @Override
    public void saveReport(DailyReport report) {
        if (report == null) {
            return;
        }
        LocalDate date = LocalDate.parse(report.date());

        // 1) upsert news_daily
        NewsDailyPo daily = new NewsDailyPo();
        daily.setReportDate(date);
        daily.setBrief(report.brief());
        daily.setTotalCount(report.totalCount());
        daily.setSourceCount(report.sourceCount());
        daily.setHtmlPath(null);
        daily.setGeneratedAt(LocalDateTime.now());
        newsDailyService.saveOrUpdate(daily);

        // 2) 回写 AI 字段到当天 news_item（按 link 归一化 hash 匹配）
        List<CuratedItem> items = report.items();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (CuratedItem item : items) {
            String hash = LinkHash.of(item.link());
            if (hash == null) {
                continue;
            }
            NewsItemPo update = new NewsItemPo();
            update.setCategory(item.category());
            update.setSummaryZh(item.summaryZh());
            update.setImportance(item.importance());
            update.setMergedCount(item.mergedCount());
            // Phase 2：标记选中 + 记录被哪天选中（候选池条目可能来自前几天，按 link_hash 匹配而非 report_date）
            update.setReviewState(NewsReviewState.SELECTED);
            update.setSelectedReportDate(date);

            LambdaQueryWrapper<NewsItemPo> where = new LambdaQueryWrapper<>();
            where.eq(NewsItemPo::getLinkHash, hash);
            newsItemService.update(update, where);
        }
    }

    /**
     * 读取某天日报：news_daily 提供 brief/计数，已整理（summary_zh 非空）的 news_item 行
     * 按 importance 倒序组装为 CuratedItem；无 news_daily 记录返回 null。
     */
    @Override
    public DailyReport getReport(String date) {
        LocalDate reportDate = LocalDate.parse(date);
        NewsDailyPo daily = newsDailyService.getById(reportDate);
        if (daily == null) {
            return null;
        }

        // 按"被哪天选中"查询：候选池条目可能在前几天采集，selected_report_date 才是展示日键
        LambdaQueryWrapper<NewsItemPo> query = new LambdaQueryWrapper<>();
        query.eq(NewsItemPo::getSelectedReportDate, reportDate)
                .isNotNull(NewsItemPo::getSummaryZh)
                .ne(NewsItemPo::getSummaryZh, "")
                .orderByDesc(NewsItemPo::getImportance);
        List<NewsItemPo> rows = newsItemService.list(query);

        List<CuratedItem> items = new ArrayList<>();
        for (NewsItemPo po : rows) {
            boolean english = "en".equalsIgnoreCase(po.getLang());
            int importance = po.getImportance() == null ? 0 : po.getImportance();
            int mergedCount = po.getMergedCount() == null ? 1 : po.getMergedCount();
            items.add(new CuratedItem(
                    po.getTitle(),
                    po.getLink(),
                    po.getSourceName(),
                    po.getCategory(),
                    po.getSummaryZh(),
                    importance,
                    english,
                    mergedCount,
                    ""
            ));
        }

        int totalCount = daily.getTotalCount() == null ? 0 : daily.getTotalCount();
        int sourceCount = daily.getSourceCount() == null ? 0 : daily.getSourceCount();
        return new DailyReport(date, daily.getBrief(), items, sourceCount, totalCount);
    }

    /** 列出最近 days 天日报元信息，按日期倒序，url = publicBase + "/" + date + ".html"。 */
    @Override
    public List<ReportMeta> listRecent(int days) {
        LambdaQueryWrapper<NewsDailyPo> query = new LambdaQueryWrapper<>();
        query.orderByDesc(NewsDailyPo::getReportDate)
                .last("limit " + Math.max(days, 0));
        List<NewsDailyPo> rows = newsDailyService.list(query);

        String base = newsConfig.getHosting().getPublicBase();
        List<ReportMeta> metas = new ArrayList<>();
        for (NewsDailyPo po : rows) {
            String dateStr = po.getReportDate().toString();
            int totalCount = po.getTotalCount() == null ? 0 : po.getTotalCount();
            int sourceCount = po.getSourceCount() == null ? 0 : po.getSourceCount();
            String url = base + "/" + dateStr + ".html";
            metas.add(new ReportMeta(dateStr, totalCount, sourceCount, url));
        }
        return metas;
    }

    /** 删除 news_item 与 news_daily 中早于 keepDays 天的记录（供调度归档调用）。 */
    @Override
    public void pruneOld(int keepDays) {
        LocalDate cutoff = LocalDate.now().minusDays(keepDays);

        LambdaQueryWrapper<NewsItemPo> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.lt(NewsItemPo::getReportDate, cutoff);
        newsItemService.remove(itemQuery);

        LambdaQueryWrapper<NewsDailyPo> dailyQuery = new LambdaQueryWrapper<>();
        dailyQuery.lt(NewsDailyPo::getReportDate, cutoff);
        newsDailyService.remove(dailyQuery);
    }

    /** 把原始 NewsItem 映射为入库 Po：原始字段 + Phase 2 生命周期字段（RAW / first&last_seen / published_at）。 */
    private NewsItemPo toRawPo(NewsItem item, LocalDate reportDate, LocalDateTime now) {
        NewsItemPo po = new NewsItemPo();
        po.setReportDate(reportDate);
        po.setSourceName(item.sourceName());
        po.setCategory(item.category());
        po.setTitle(item.title());
        po.setLink(item.link());
        po.setLinkHash(item.linkHash());
        po.setDescription(item.description());
        po.setPubDate(item.pubDate());
        po.setLang(item.lang());
        po.setReviewState(NewsReviewState.RAW);
        po.setFirstSeenAt(now);
        po.setLastSeenAt(now);
        po.setPublishedAt(NewsDateParser.parse(item.pubDate()));
        return po;
    }
}
