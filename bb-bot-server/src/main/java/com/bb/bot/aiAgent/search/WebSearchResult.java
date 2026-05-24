package com.bb.bot.aiAgent.search;

/**
 * 单条搜索结果。
 *
 * <p>{@code content} 仅 Tavily 这类「返回正文」的后端会填，其余后端为 null —— 拿到
 * url 后通常还需要 http_fetch 抓正文。</p>
 */
public record WebSearchResult(String title, String url, String snippet, String content) {

    /** 不带正文的常规结果（Brave / SerpAPI / DuckDuckGo）。 */
    public static WebSearchResult of(String title, String url, String snippet) {
        return new WebSearchResult(title, url, snippet, null);
    }
}
