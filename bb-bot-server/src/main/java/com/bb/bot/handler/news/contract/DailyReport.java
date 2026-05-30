package com.bb.bot.handler.news.contract;

import java.util.List;

/**
 * 一天的完整日报数据。
 *
 * <p>由 {@link NewsAiCurator#curate} 产出，由 {@link NewsStore#saveReport} 持久化，
 * 由 {@link NewsPageBuilder#buildDaily} 渲染成 HTML。</p>
 *
 * @param date        日期，格式 "yyyy-MM-dd"
 * @param brief       今日速览导语（降级时可为空串）
 * @param items       已排序、已分类的条目列表
 * @param sourceCount 聚合源数量
 * @param totalCount  精选条目总数
 */
public record DailyReport(
        String date,
        String brief,
        List<CuratedItem> items,
        int sourceCount,
        int totalCount
) {
}
