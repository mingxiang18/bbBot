package com.bb.bot.handler.news.curate;

import com.bb.bot.handler.news.contract.NewsCategory;
import com.bb.bot.handler.news.contract.NewsItem;

import java.util.Map;

/**
 * 构造 AI 整理用的 system / user 提示词（Phase 3：ID 化）。纯函数式、无状态，便于单测。
 *
 * <p>输入条目带稳定 {@code id}（n1/n2/…），LLM 只用 id 引用并给出整理判断；
 * <b>不</b>要求也<b>不</b>采信 LLM 回吐的 title/link/sourceName，这些由服务端按 id 回填，
 * 杜绝幻觉链接 / prompt injection 伪造来源。</p>
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
                这是一份精选日报，不是全量列表——宁缺毋滥。每条输入都带一个唯一 id（如 n1、n2），
                你只能引用这些 id，**不要**编造、改写或输出任何标题、链接、来源名——这些服务端会按 id 回填。请完成：

                ① 语义聚类去重：把描述同一事件的多源条目合并为一条，选信息最完整的作为代表，代表条目的 id 放在 item.id；
                   被合并的所有条目 id（含代表自己）放进 clusterIds；note 标注合并情况，如 "去重 2→1"、"多源合并"；未合并 clusterIds 只含自身、note 为空串 ""。
                ② 精选取舍（重要）：只保留有真实新闻价值、信息量充足的条目；**主动丢弃**以下低价值条目，不要放进 items：
                   - 纯仪式性/礼节性、地方性小活动小会议（如某地某展会开幕、某赛事举办、某典礼）；
                   - 标题党、无实质信息、内容空洞的条目；广告/软文/榜单/纯预告；与日报受众无关的边角料。
                   每个分类（按 importance）只保留最有价值的约 5-8 条；某分类当天没有够格的就少给或不给。
                   被丢弃的条目可放进 rejected（{id, reason}），reason 取 low_value/duplicate/stale/invalid 之一；rejected 可省略。
                ③ 中文摘要：给每条写一句话中文摘要 summaryZh（英文源也用中文摘要），不超过 100 字。
                ④ 分类：把每条归入下列 8 个固定分类之一（category 取值必须是其中之一）：
                   %s
                ⑤ 重要性：给每条打 importance 整数 1-5（5 最重要），真实反映新闻价值，便于排序展示。
                ⑥ 今日速览：写一句话中文 brief 概括今日要点。如果今天没有任何够格条目，items 给空数组 []（合法）。

                严格只输出 JSON，不要输出任何解释、前后缀或 Markdown 代码围栏之外的文字。JSON schema 如下：
                {"brief":"…","items":[{"id":"n1","clusterIds":["n1"],"category":"…","summaryZh":"…","importance":1,"note":""}],"rejected":[{"id":"n2","reason":"low_value"}]}
                """.formatted(CATEGORY_KEYS);
    }

    /**
     * 待整理条目 user 消息：按 id 列出（id → NewsItem 的有序映射）。
     *
     * <p>只给 source/category/lang/title/description（含 publishedAt，若有）。<b>不给 link</b>——
     * 模型不需要、也不应回吐链接。</p>
     *
     * @param byId 稳定 id → 条目，顺序即喂入顺序
     */
    public static String user(Map<String, NewsItem> byId) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是当日待整理资讯，共 ").append(byId.size()).append(" 条（用 id 引用）：\n\n");
        for (Map.Entry<String, NewsItem> e : byId.entrySet()) {
            NewsItem item = e.getValue();
            sb.append("id: ").append(e.getKey()).append('\n');
            sb.append("source: ").append(nullToEmpty(item.sourceName())).append('\n');
            sb.append("category: ").append(nullToEmpty(item.category())).append('\n');
            sb.append("lang: ").append(nullToEmpty(item.lang())).append('\n');
            sb.append("title: ").append(nullToEmpty(item.title())).append('\n');
            sb.append("description: ").append(nullToEmpty(item.description())).append('\n');
            if (item.pubDate() != null && !item.pubDate().isBlank()) {
                sb.append("publishedAt: ").append(item.pubDate()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
