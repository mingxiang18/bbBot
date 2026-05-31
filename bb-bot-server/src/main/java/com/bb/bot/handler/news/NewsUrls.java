package com.bb.bot.handler.news;

import com.bb.bot.config.NewsConfig;

/**
 * 日报访问 URL 拼装工具：把相对路径与配置的对外基地址拼成完整可点链接。
 */
public final class NewsUrls {

    private NewsUrls() {
    }

    /** 某天日报页的相对路径，如 {@code /news/2026-05-30.html}。 */
    public static String pathFor(NewsConfig config, String date) {
        return config.getHosting().getPublicBase() + "/" + date + ".html";
    }

    /**
     * 某天日报页的完整 URL。配置了 externalBaseUrl 时拼成可点击的绝对地址，
     * 否则返回相对路径。
     */
    public static String fullFor(NewsConfig config, String date) {
        String base = config.getHosting().getExternalBaseUrl();
        String path = pathFor(config, date);
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.replaceAll("/+$", "") + path;
    }
}
