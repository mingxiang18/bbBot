package com.bb.bot.handler.news.contract;

/**
 * 采集到的原始资讯条目。
 *
 * <p>由 {@link NewsFetcher} 产出，作为 {@link NewsStore#dedupAndSave} 与
 * {@link NewsAiCurator#curate} 的输入。仅承载 RSS 提供的字段，不含任何整理结果。</p>
 *
 * @param sourceName 源名称，如 "中新网"
 * @param category   源声明的分类（可空；最终分类以 AI 整理结果为准）
 * @param title      原始标题（英文源保留英文原标题）
 * @param link       逐篇文章的真实 URL（必填，不得为站点首页占位）
 * @param description 原始摘要（可空）
 * @param pubDate    原始发布时间字符串（保留源格式，不强制解析）
 * @param lang       语言，"zh" 或 "en"
 * @param linkHash   由 link 归一化后计算的去重键（由 NewsFetcher 填充）
 */
public record NewsItem(
        String sourceName,
        String category,
        String title,
        String link,
        String description,
        String pubDate,
        String lang,
        String linkHash
) {
}
