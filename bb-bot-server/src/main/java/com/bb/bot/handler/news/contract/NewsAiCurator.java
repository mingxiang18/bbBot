package com.bb.bot.handler.news.contract;

import java.util.List;

/**
 * AI 整理（任务 T3 实现）。
 *
 * <p>调用项目现有 LLM 完成：L2 语义聚类去重、中文摘要、分类、重要性排序、生成今日速览。
 * LLM 不可用（超时 / 解析失败）时必须降级：返回由原始条目直接映射的 {@link DailyReport}，
 * 不得抛出异常导致整条流水线失败。</p>
 */
public interface NewsAiCurator {

    /**
     * 整理当日条目。
     *
     * @param items 去重后的当日条目（仅含原始字段）
     * @return 完整 DailyReport；LLM 失败时为降级版本（brief 空、摘要用原始描述）
     */
    DailyReport curate(List<NewsItem> items);
}
