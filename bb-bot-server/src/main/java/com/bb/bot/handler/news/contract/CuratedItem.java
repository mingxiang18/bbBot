package com.bb.bot.handler.news.contract;

/**
 * AI 整理后的资讯条目。
 *
 * <p>由 {@link NewsAiCurator} 产出，作为 {@link DailyReport} 的元素、
 * {@link NewsPageBuilder} 渲染的输入单元。</p>
 *
 * @param title       标题（英文源保留英文原标题）
 * @param link        逐篇文章真实 URL（多源合并时取代表条目的链接）
 * @param sourceName  来源名称
 * @param category    最终分类，取值必须 ∈ {@link NewsCategory#ALL}
 * @param summaryZh   中文一句话摘要
 * @param importance  重要性 1–5，用于排序与 ★ 展示
 * @param english     是否英文源（决定 EN 角标）
 * @param mergedCount 多源合并的条目数，1 表示未合并
 * @param note        角标备注，如 "多源合并" / "去重 2→1" / ""（不可为 null）
 */
public record CuratedItem(
        String title,
        String link,
        String sourceName,
        String category,
        String summaryZh,
        int importance,
        boolean english,
        int mergedCount,
        String note
) {
}
