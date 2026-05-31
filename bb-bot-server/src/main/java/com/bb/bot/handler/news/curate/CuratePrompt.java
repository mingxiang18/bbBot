package com.bb.bot.handler.news.curate;

import com.bb.bot.handler.news.contract.NewsCategory;
import com.bb.bot.handler.news.contract.NewsItem;

import java.util.List;

/**
 * 构造 AI 整理用的 system / user 提示词。纯函数式、无状态，便于单测。
 *
 * <p>由 {@link NewsAiCuratorImpl} 调用：system 说明任务与严格 JSON 输出约束，
 * user 把待整理条目编号列出。</p>
 */
public final class CuratePrompt {

    private CuratePrompt() {
    }

    /** 固定分类键的逗号拼接，如 "AI/国际/时政/...". */
    private static final String CATEGORY_KEYS = String.join("/", NewsCategory.ALL);

    /**
     * 任务说明 system 消息。
     */
    public static String system() {
        return """
                你是一名中文科技/财经资讯主编，负责把当日多源 RSS 资讯精选整理成一份"精炼"日报。
                这是一份精选日报，不是全量列表——宁缺毋滥。请完成以下任务：

                ① 语义聚类去重：把描述同一事件的多源条目合并为一条，保留信息最完整的代表条目的 link。
                   合并时用 mergedCount 记录被合并的条目数（未合并为 1），note 标注合并情况，如 "去重 2→1"、"多源合并"；未合并则 note 为空串 ""。
                ② 精选取舍（重要）：只保留有真实新闻价值、信息量充足的条目；**主动丢弃**以下低价值条目，不要放进 items：
                   - 纯仪式性/礼节性、地方性小活动小会议（如某地某展会开幕、某赛事举办、某典礼）；
                   - 标题党、无实质信息、内容空洞的条目；广告/软文/榜单/纯预告；与日报受众无关的边角料。
                   每个分类（按 importance）只保留最有价值的约 5-8 条；某分类当天没有够格的就少给或不给。
                ③ 中文摘要：给每条写一句话中文摘要 summaryZh（英文源也用中文摘要）。
                   标题 title 保持原文：英文源保留英文原标题，不要翻译。
                ④ 分类：把每条归入下列 8 个固定分类之一（category 取值必须是其中之一）：
                   %s
                ⑤ 重要性：给每条打 importance 整数 1-5（5 最重要）。importance 要真实反映新闻价值，
                   重大/影响广的给高分，边角料给低分，便于按重要性排序展示。
                ⑥ 今日速览：写一句话中文 brief 概括今日要点。

                english 字段：英文源为 true，中文源为 false。
                sourceName 字段：保留该条来源名称（合并时取代表条目来源）。

                严格只输出 JSON，不要输出任何解释、前后缀或 Markdown 代码围栏之外的文字。JSON schema 如下：
                {"brief":"…","items":[{"title":"…","link":"…","sourceName":"…","category":"…","summaryZh":"…","importance":1,"english":false,"mergedCount":1,"note":""}]}
                """.formatted(CATEGORY_KEYS);
    }

    /**
     * 待整理条目 user 消息：编号列出。
     *
     * @param items 已截断到 maxItems 的输入条目
     */
    public static String user(List<NewsItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是当日待整理资讯，共 ").append(items.size()).append(" 条：\n\n");
        int idx = 1;
        for (NewsItem item : items) {
            sb.append("【").append(idx++).append("】\n");
            sb.append("source: ").append(nullToEmpty(item.sourceName())).append('\n');
            sb.append("category: ").append(nullToEmpty(item.category())).append('\n');
            sb.append("lang: ").append(nullToEmpty(item.lang())).append('\n');
            sb.append("title: ").append(nullToEmpty(item.title())).append('\n');
            sb.append("description: ").append(nullToEmpty(item.description())).append('\n');
            sb.append("link: ").append(nullToEmpty(item.link())).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
