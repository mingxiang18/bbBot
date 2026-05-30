package com.bb.bot.handler.news.contract;

/**
 * 历史日报的元信息，用于跨天往期导航与归档索引页。
 *
 * <p>由 {@link NewsStore#listRecent} 产出；{@link NewsPageBuilder} 用它渲染
 * 日期切换器与归档列表。</p>
 *
 * @param date       日期 "yyyy-MM-dd"
 * @param totalCount 当日精选条数
 * @param sourceCount 当日聚合源数
 * @param url        该日报的访问 URL
 */
public record ReportMeta(
        String date,
        int totalCount,
        int sourceCount,
        String url
) {
}
