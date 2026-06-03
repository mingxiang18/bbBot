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

    /**
     * 候选池（Phase 2）：返回时间窗内仍可评估的 {@code RAW} 条目，供 AI/服务端精选。
     *
     * <p>把"见过"与"评估过"分开：超过 {@code ai.maxItems} 截断、故障期、被同日刷新覆盖的
     * 条目，只要还在时间窗内（按 {@code candidateWindowHours} 或源级 {@code windowHours}），
     * 就能重新进入候选，不再因一次入库就被永久判死。已 SELECTED/REJECTED 的不返回。</p>
     *
     * @param date 目标日报日期 "yyyy-MM-dd"（当前实现按"现在"算时间窗）
     * @return 候选 NewsItem 列表
     */
    List<NewsItem> listEligibleForReport(String date);

    /** 持久化某天的整理结果（速览 + 统计 + 条目的 AI 字段），并把选中条目标记为 SELECTED。 */
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
