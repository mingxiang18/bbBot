package com.bb.bot.handler.news.contract;

import java.util.List;

/**
 * 资讯采集器（任务 T2 实现）。
 *
 * <p>从配置的源列表（官方直连 + 自建 RSSHub）拉取并解析 RSS，产出原始条目。
 * 单源失败必须隔离（记录日志并跳过），不得因单源异常中断整体。</p>
 */
public interface NewsFetcher {

    /**
     * 拉取全部已配置源并解析。
     *
     * @return 原始条目列表（已填充 linkHash），永不返回 null；全部源失败时返回空列表
     */
    List<NewsItem> fetchAll();
}
