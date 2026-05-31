package com.bb.bot.handler.news.render;

import com.bb.bot.config.NewsConfig;
import com.bb.bot.handler.news.contract.CuratedItem;
import com.bb.bot.handler.news.contract.DailyReport;
import com.bb.bot.handler.news.contract.NewsCategory;
import com.bb.bot.handler.news.contract.NewsPageBuilder;
import com.bb.bot.handler.news.contract.ReportMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bb.bot.handler.news.render.NewsHtmlTemplate.escape;

/**
 * {@link NewsPageBuilder} 的默认实现（任务 T4）。
 *
 * <p>把 {@link DailyReport} / {@link ReportMeta} 渲染成自包含 HTML（内联 CSS + 原生 JS，
 * 无外部框架 / CDN）。结构与样式与已验收 demo 一致，并在其上补齐三件交互套件：</p>
 * <ul>
 *   <li><b>分类筛选</b>：按 {@link NewsCategory#ALL} 顺序生成 Tab，只显示有条目的分类 + "全部"，
 *       点击 Tab 切换 {@code data-cat} 过滤、实时计数、Tab 容器 sticky。</li>
 *   <li><b>站内搜索</b>：每张卡片预渲染 {@code data-search}（标题+摘要，小写），脚本按关键词做
 *       子串匹配，与分类筛选取<b>交集</b>。</li>
 *   <li><b>跨天往期导航</b>：把 {@code availableDates} 注入为内联 JS 数组，渲染上一天 / 下一天 /
 *       日期下拉，切换即 {@code location.href = './<date>.html'}（相对链接）。</li>
 * </ul>
 *
 * <p>每个分类最多展示 {@link NewsConfig#getPerCategoryLimit()} 条；所有数据文本经
 * {@link NewsHtmlTemplate#escape(String)} 转义。</p>
 */
@Component
public class NewsPageBuilderImpl implements NewsPageBuilder {

    private final NewsConfig newsConfig;

    public NewsPageBuilderImpl(NewsConfig newsConfig) {
        this.newsConfig = newsConfig;
    }

    @Override
    public String buildDaily(DailyReport report, List<ReportMeta> availableDates) {
        int perCat = Math.max(1, newsConfig.getPerCategoryLimit());

        // 按 NewsCategory.ALL 顺序分组，归一化非法分类，单类截断到 perCat。
        Map<String, List<CuratedItem>> grouped = groupByCategory(report.items(), perCat);

        int shownTotal = grouped.values().stream().mapToInt(List::size).sum();

        StringBuilder html = new StringBuilder(8192);
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"zh-CN\">\n")
                .append("<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>每日资讯 · ").append(escape(report.date())).append("</title>\n")
                .append(NewsHtmlTemplate.STYLE).append("\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("<div class=\"wrap\">\n");

        // 顶部标题 + 日期
        html.append("  <header>\n")
                .append("    <div class=\"kicker\">BB·DAILY</div>\n")
                .append("    <h1>每日资讯日报</h1>\n")
                .append("    <div class=\"date\">").append(escape(report.date()))
                .append(" · 08:00 自动生成</div>\n")
                .append(renderDateNav(report.date(), availableDates))
                .append("  </header>\n\n");

        // 今日速览
        html.append("  <div class=\"brief\">\n")
                .append("    <b>📌 今日速览（AI 生成）</b>　")
                .append(escape(report.brief()))
                .append("　共聚合 <b>").append(report.sourceCount()).append(" 个源</b>，精选 <b>")
                .append(shownTotal).append(" 条</b>。\n")
                .append("  </div>\n\n");

        // 站内搜索框
        html.append("  <div class=\"search\">\n")
                .append("    <input id=\"search\" type=\"search\" placeholder=\"🔍 搜索标题或摘要…\" autocomplete=\"off\">\n")
                .append("    <span class=\"clr\" id=\"search-clear\" title=\"清空\">×</span>\n")
                .append("  </div>\n\n");

        // 分类 Tab（仅有条目的分类 + 全部）
        html.append("  <div class=\"tabs\" id=\"tabs\">\n")
                .append("    <div class=\"tab active\" data-cat=\"all\">全部 ").append(shownTotal).append("</div>\n");
        for (String cat : NewsCategory.ALL) {
            List<CuratedItem> items = grouped.get(cat);
            if (items == null || items.isEmpty()) {
                continue;
            }
            String label = NewsCategory.LABELS.getOrDefault(cat, cat);
            html.append("    <div class=\"tab\" data-cat=\"").append(escape(cat)).append("\">")
                    .append(escape(label)).append(" ").append(items.size()).append("</div>\n");
        }
        html.append("  </div>\n")
                .append("  <div class=\"count\" id=\"count\"></div>\n\n");

        // 卡片列表
        html.append("  <div id=\"list\">\n");
        for (String cat : NewsCategory.ALL) {
            List<CuratedItem> items = grouped.get(cat);
            if (items == null || items.isEmpty()) {
                continue;
            }
            html.append("\n    <!-- ===== ").append(cat).append(" ===== -->\n");
            for (CuratedItem item : items) {
                html.append(renderCard(item));
            }
        }
        html.append("  </div>\n")
                .append("  <div class=\"empty\" id=\"empty\">没有匹配的资讯</div>\n\n");

        // 页脚（数据源列表）
        String srcList = distinctSources(report.items());
        html.append("  <footer>\n")
                .append("    📰 数据源：").append(escape(srcList)).append("<br>\n")
                .append("    每日 08:00 自动聚合 → AI 整理 → 生成本页　|　bbBot 自动生成 · 链接直达原文 · 摘要以原文为准\n")
                .append("  </footer>\n")
                .append("</div>\n");

        // 脚本：注入日期数组 + 当前日期
        String script = NewsHtmlTemplate.SCRIPT
                .replace("__DATES__", jsDateArray(availableDates))
                .replace("__CURRENT__", "'" + jsString(report.date()) + "'");
        html.append(script).append("\n")
                .append("</body>\n")
                .append("</html>");

        return html.toString();
    }

    @Override
    public String buildArchiveIndex(List<ReportMeta> metas) {
        List<ReportMeta> sorted = new ArrayList<>(metas == null ? List.of() : metas);
        // 倒序（日期字符串 yyyy-MM-dd 可直接字典序比较）。
        sorted.sort((a, b) -> b.date().compareTo(a.date()));

        StringBuilder html = new StringBuilder(2048);
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"zh-CN\">\n")
                .append("<head>\n")
                .append("<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("<title>资讯日报 · 归档</title>\n")
                .append(NewsHtmlTemplate.STYLE).append("\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("<div class=\"wrap\">\n")
                .append("  <header>\n")
                .append("    <div class=\"kicker\">BB·DAILY</div>\n")
                .append("    <h1>历史归档</h1>\n")
                .append("    <div class=\"date\">共 ").append(sorted.size()).append(" 天</div>\n")
                .append("  </header>\n\n")
                .append("  <div id=\"list\">\n");

        for (ReportMeta m : sorted) {
            html.append("    <a class=\"arch-item\" href=\"./").append(escape(m.date())).append(".html\">\n")
                    .append("      <span class=\"d\">").append(escape(m.date())).append("</span>\n")
                    .append("      <span class=\"s\">").append(m.totalCount()).append(" 条 · ")
                    .append(m.sourceCount()).append(" 源</span>\n")
                    .append("    </a>\n");
        }

        html.append("  </div>\n")
                .append("  <footer>\n")
                .append("    bbBot 自动生成 · 每日 08:00 聚合\n")
                .append("  </footer>\n")
                .append("</div>\n")
                .append("</body>\n")
                .append("</html>");

        return html.toString();
    }

    /** 按 {@link NewsCategory#ALL} 顺序分组，归一化非法分类，单类截断到 limit。 */
    private Map<String, List<CuratedItem>> groupByCategory(List<CuratedItem> items, int limit) {
        Map<String, List<CuratedItem>> grouped = new LinkedHashMap<>();
        for (String cat : NewsCategory.ALL) {
            grouped.put(cat, new ArrayList<>());
        }
        if (items != null) {
            for (CuratedItem item : items) {
                String cat = NewsCategory.normalize(item.category());
                grouped.get(cat).add(item);   // 先全部归类，截断前再按重要性排序
            }
        }
        // 每类按 importance 倒序（稳定排序：同分保持 AI 返回顺序），再截断到 limit，
        // 保证每个分类留下的是该类「最重要」的若干条，而非采集/返回顺序的前几条。
        for (List<CuratedItem> bucket : grouped.values()) {
            bucket.sort(Comparator.comparingInt(CuratedItem::importance).reversed());
            if (bucket.size() > limit) {
                bucket.subList(limit, bucket.size()).clear();
            }
        }
        return grouped;
    }

    /** 单张卡片 HTML（含尾随换行）。 */
    private String renderCard(CuratedItem item) {
        String cat = NewsCategory.normalize(item.category());
        String link = (item.link() == null || item.link().isBlank()) ? "#" : item.link();
        String title = item.title() == null ? "" : item.title();
        String summary = item.summaryZh() == null ? "" : item.summaryZh();

        String enBadge = item.english() ? "<span class=\"en\">EN</span>" : "";
        String stars = "★".repeat(Math.max(0, Math.min(5, item.importance())));
        String note = item.note();
        String noteHtml = (note != null && !note.isBlank())
                ? "<span>· " + escape(note) + "</span>"
                : "";

        // 搜索索引（标题 + 摘要，小写），单独转义后放入 data-search。
        String searchHay = escape((title + " " + summary).toLowerCase());

        return "    <article class=\"card\" data-cat=\"" + escape(cat)
                + "\" data-search=\"" + searchHay + "\">\n"
                + "      <div class=\"meta\"><span class=\"src\">" + escape(item.sourceName())
                + "</span><span class=\"cat\">" + escape(cat) + "</span>" + enBadge
                + "<span class=\"stars\">" + stars + "</span>" + noteHtml + "</div>\n"
                + "      <div class=\"title\"><a href=\"" + escape(link)
                + "\" target=\"_blank\" rel=\"noopener\">" + escape(title) + "</a></div>\n"
                + "      <div class=\"sum\"><span class=\"ai\">AI摘要</span>" + escape(summary) + "</div>\n"
                + "      <a class=\"orig\" href=\"" + escape(link)
                + "\" target=\"_blank\" rel=\"noopener\">阅读原文 →</a>\n"
                + "    </article>\n";
    }

    /** 跨天往期导航：上一天 / 下一天 / 日期下拉 + 归档入口。 */
    private String renderDateNav(String current, List<ReportMeta> availableDates) {
        List<String> dates = new ArrayList<>();
        if (availableDates != null) {
            for (ReportMeta m : availableDates) {
                dates.add(m.date());
            }
        }
        // availableDates 约定倒序；以升序定位前后天更直观。
        List<String> asc = new ArrayList<>(dates);
        asc.sort(String::compareTo);
        int idx = asc.indexOf(current);
        String prev = (idx > 0) ? asc.get(idx - 1) : null;             // 更早的一天
        String next = (idx >= 0 && idx < asc.size() - 1) ? asc.get(idx + 1) : null; // 更晚的一天

        StringBuilder sb = new StringBuilder();
        sb.append("    <div class=\"nav\">\n");

        // 上一天（更早）
        if (prev != null) {
            sb.append("      <a href=\"./").append(escape(prev)).append(".html\">← 前一天</a>\n");
        } else {
            sb.append("      <span class=\"navbtn disabled\">← 前一天</span>\n");
        }

        // 日期下拉（倒序展示）
        sb.append("      <select id=\"date-picker\" aria-label=\"选择日期\">\n");
        for (String d : dates) { // dates 即 availableDates 顺序（倒序）
            boolean sel = d.equals(current);
            sb.append("        <option value=\"").append(escape(d)).append("\"")
                    .append(sel ? " selected" : "").append(">").append(escape(d)).append("</option>\n");
        }
        if (idx < 0) {
            // 当前日期不在列表里：补一个 selected 项，保证下拉显示正确。
            sb.append("        <option value=\"").append(escape(current)).append("\" selected>")
                    .append(escape(current)).append("</option>\n");
        }
        sb.append("      </select>\n");

        // 下一天（更晚）
        if (next != null) {
            sb.append("      <a href=\"./").append(escape(next)).append(".html\">后一天 →</a>\n");
        } else {
            sb.append("      <span class=\"navbtn disabled\">后一天 →</span>\n");
        }

        // 归档入口
        sb.append("      <a class=\"arch\" href=\"./index.html\">📚 归档</a>\n");

        sb.append("    </div>\n");
        return sb.toString();
    }

    /** 去重并按原始出现顺序拼接源名，用 " · " 连接。 */
    private String distinctSources(List<CuratedItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(CuratedItem::sourceName)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.joining(" · "));
    }

    /** 把日期列表渲染为内联 JS 数组字面量（按入参顺序，通常倒序）。 */
    private String jsDateArray(List<ReportMeta> availableDates) {
        if (availableDates == null || availableDates.isEmpty()) {
            return "[]";
        }
        String inner = availableDates.stream()
                .map(m -> "'" + jsString(m.date()) + "'")
                .collect(Collectors.joining(","));
        return "[" + inner + "]";
    }

    /** JS 单引号字符串转义（防注入闭合脚本 / 单引号 / 反斜杠）。 */
    private String jsString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '<' -> sb.append("\\u003C"); // 防 </script> 提前闭合
                case '>' -> sb.append("\\u003E");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
