package com.bb.bot.handler.news.contract;

import java.util.List;

/**
 * 交互式 HTML 页面生成（任务 T4 实现）。
 *
 * <p>产出自包含的 HTML（内联 CSS + 原生 JS，无外部框架）。交互三件套——分类筛选、
 * 站内搜索、跨天往期导航——全部由本模块实现。</p>
 */
public interface NewsPageBuilder {

    /**
     * 生成单日日报页。
     *
     * @param report         当日报告
     * @param availableDates 可供往期导航的日期元信息（倒序），用于日期切换器
     * @return 自包含 HTML 字符串
     */
    String buildDaily(DailyReport report, List<ReportMeta> availableDates);

    /**
     * 生成归档索引页（近 N 天列表）。
     *
     * @param metas 历史日报元信息（倒序）
     * @return 自包含 HTML 字符串
     */
    String buildArchiveIndex(List<ReportMeta> metas);
}
