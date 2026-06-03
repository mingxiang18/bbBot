package com.bb.bot.handler.news.contract;

/**
 * 一次日报生成的可观测指标（Phase 4）。
 *
 * <p>每次 {@code generateNow} 记录一条结构化日志，用于判断是源失效、AI 过严、候选太少，
 * 还是降级触发。首版以结构化日志承载，无需单独建 {@code news_generation_run} 表。</p>
 *
 * @param fetched   采集总数
 * @param fresh     本次新增（去重后）条目数
 * @param eligible  候选池数量
 * @param selected  最终展示数量
 * @param aiStatus  AI 状态：success / empty / fallback / disabled
 * @param published 是否实际出页（dryRun / 空精选 / 失败时为 false）
 * @param url       出页访问地址（未出页为 null）
 */
public record NewsRunStats(
        int fetched,
        int fresh,
        int eligible,
        int selected,
        String aiStatus,
        boolean published,
        String url
) {
    /** 紧凑可读的一行（供日志与 /news/run?dryRun 返回）。 */
    public String toLine() {
        return String.format(
                "fetched=%d fresh=%d eligible=%d selected=%d ai=%s published=%s url=%s",
                fetched, fresh, eligible, selected, aiStatus, published, url == null ? "-" : url);
    }
}
