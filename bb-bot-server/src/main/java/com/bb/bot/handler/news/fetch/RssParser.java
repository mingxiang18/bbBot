package com.bb.bot.handler.news.fetch;

import com.bb.bot.handler.news.contract.LinkHash;
import com.bb.bot.handler.news.contract.NewsItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * RSS 2.0 解析器（纯函数式，无 Spring 依赖，可单测）。
 *
 * <p>输入 RSS XML 字符串与源元信息（sourceName/category/lang），输出 {@link NewsItem} 列表。
 * 解析使用 jsoup 的 XML parser（项目已有依赖），对畸形/缺字段/CDATA/HTML 实体均做容错。
 * 每条的 {@code linkHash} 在此计算：link 去掉 query 参数后做 SHA-1，取 40 位小写 hex。</p>
 */
public class RssParser {

    /** description 清洗后最大保留字符数。 */
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    /** description 常见尾巴片段，命中即从该处截断丢弃。 */
    private static final String[] TAIL_MARKERS = {
            "查看全文",
            "阅读原文",
            "阅读全文",
            "点击查看",
            "more…",
            "more&hellip;",
            "Read more"
    };

    private RssParser() {
    }

    /**
     * 解析 RSS XML，组装 NewsItem 列表。
     *
     * @param xml        RSS XML 字符串（可为 null/空/畸形，均不抛异常）
     * @param sourceName 源名称
     * @param category   源声明分类
     * @param lang       语言（"zh" / "en"）
     * @return NewsItem 列表，永不返回 null；无法解析时返回空列表
     */
    public static List<NewsItem> parse(String xml, String sourceName, String category, String lang) {
        List<NewsItem> result = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return result;
        }

        Document doc;
        try {
            doc = Jsoup.parse(xml, "", Parser.xmlParser());
        } catch (Exception e) {
            return result;
        }

        Elements items = doc.select("item");
        for (Element item : items) {
            try {
                String link = textOf(item, "link");
                if (link == null || link.isBlank()) {
                    // 没有可定位的真实文章 URL，无法去重，跳过该条
                    continue;
                }
                String title = nullToEmpty(textOf(item, "title"));
                String pubDate = nullToEmpty(textOf(item, "pubDate"));
                String description = cleanDescription(textOf(item, "description"));
                String linkHash = LinkHash.of(link);

                result.add(new NewsItem(
                        sourceName,
                        category,
                        title,
                        link,
                        description,
                        pubDate,
                        lang,
                        linkHash
                ));
            } catch (Exception e) {
                // 单条解析失败不影响其它条目
            }
        }
        return result;
    }

    /**
     * 取 item 下首个指定标签的文本。jsoup XML parser 会把 CDATA 与 HTML 实体解出为普通文本。
     */
    private static String textOf(Element item, String tag) {
        Element el = item.select(tag).first();
        if (el == null) {
            return null;
        }
        // text() 已对 CDATA / 实体反转义；wholeText 保留内部内容，统一用 text() 拿纯文本
        String t = el.text();
        return t == null ? null : t.trim();
    }

    /**
     * 清洗 description：去 HTML 标签、反转义、清掉常见尾巴、压缩空白、截断。
     */
    static String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        // description 内部可能仍是转义后的 HTML 片段（如 &lt;a&gt;查看全文&lt;/a&gt;），
        // 经 jsoup XML text() 反转义后变成真实 HTML 标签，这里再用 jsoup 提取纯文本去标签。
        String text;
        try {
            text = Jsoup.parse(raw).text();
        } catch (Exception e) {
            text = raw;
        }

        // 清掉常见尾巴：命中标记则从标记处截断
        for (String marker : TAIL_MARKERS) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                text = text.substring(0, idx);
            }
        }

        // 清掉「#欢迎关注…」之类话题尾巴：从首个 '#' 起且后续较短时丢弃
        int hashIdx = text.indexOf('#');
        if (hashIdx >= 0) {
            text = text.substring(0, hashIdx);
        }

        // 压缩空白
        text = text.replaceAll("\\s+", " ").trim();

        // 截断
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            text = text.substring(0, MAX_DESCRIPTION_LENGTH).trim() + "…";
        }
        return text;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
