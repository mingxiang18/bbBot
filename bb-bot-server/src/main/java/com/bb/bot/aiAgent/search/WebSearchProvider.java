package com.bb.bot.aiAgent.search;

import java.util.List;

/**
 * 搜索后端抽象。
 *
 * <p>每个实现是一个 Spring bean。{@code WebSearchTool} 把所有 provider 按
 * {@link #priority()} 升序排，取第一个 {@link #isAvailable()} 的执行；若它抛异常
 * 或返回空结果，则降级到下一个。参考 OpenClaw 的 provider 插件化设计。</p>
 */
public interface WebSearchProvider {

    /** 后端名，用于日志与返回里的 {@code backend} 字段。 */
    String name();

    /** 是否就绪（如 API key 已配置）。未就绪的 provider 会被跳过。 */
    boolean isAvailable();

    /** 优先级，数字越小越优先。 */
    int priority();

    /**
     * 执行一次搜索。
     *
     * @param query      搜索词
     * @param maxResults 结果条数上限
     * @return 结果列表（可能为空）；网络 / 鉴权失败应抛异常，由调用方降级
     */
    List<WebSearchResult> search(String query, int maxResults) throws Exception;
}
