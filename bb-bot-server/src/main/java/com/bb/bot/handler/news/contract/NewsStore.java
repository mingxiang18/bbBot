package com.bb.bot.handler.news.contract;

import java.util.List;

/**
 * 资讯持久化与去重（任务 T1 实现）。
 *
 * <p>仅负责 L1 物理去重（同 link）与持久化、归档查询；语义级聚类（L2）由
 * {@link NewsAiCurator} 负责，二者层次不同、互不重合。</p>
 */
public interface NewsStore {

    /**
     * L1 物理去重并保存：按 {@link NewsItem#linkHash} 过滤掉已存在的条目，
     * 将新条目入库。
     *
     * @param items 当日采集到的原始条目
     * @return 去重后的「新」条目（即本次真正新增、需要送 AI 整理的部分）
     */
    List<NewsItem> dedupAndSave(List<NewsItem> items);

    /** 持久化某天的整理结果（速览 + 统计 + 条目的 AI 字段）。 */
    void saveReport(DailyReport report);

    /**
     * 读取某天的日报。
     *
     * @param date "yyyy-MM-dd"
     * @return 该日 DailyReport；不存在时返回 null
     */
    DailyReport getReport(String date);

    /**
     * 列出最近若干天的日报元信息，按日期倒序。
     *
     * @param days 天数（默认 30）
     */
    List<ReportMeta> listRecent(int days);

    /**
     * 删除早于 keepDays 天的历史记录（news_item 与 news_daily），用于归档保留裁剪。
     *
     * @param keepDays 保留天数
     */
    void pruneOld(int keepDays);
}
