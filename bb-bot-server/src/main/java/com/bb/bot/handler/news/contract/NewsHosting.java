package com.bb.bot.handler.news.contract;

/**
 * 页面托管（任务 T5 实现）。
 *
 * <p>把生成的 HTML 落盘到静态目录（{@code file.path} 下的 news 子目录），更新归档索引与
 * latest 入口，并返回当日页的访问 URL。对外访问由 T5 的 Controller 暴露（无鉴权）。</p>
 */
public interface NewsHosting {

    /**
     * 发布当日日报。
     *
     * @param date             日期 "yyyy-MM-dd"
     * @param dailyHtml        单日页 HTML
     * @param archiveIndexHtml 归档索引页 HTML
     * @return 当日页对外访问 URL
     */
    String publish(String date, String dailyHtml, String archiveIndexHtml);
}
