package com.bb.bot.handler.news.curate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 整理结果的 JSON 映射 DTO 与鲁棒解析辅助。
 *
 * <p>对应 {@link CuratePrompt#system()} 约定的 schema：
 * {@code {"brief":"…","items":[{title,link,sourceName,category,summaryZh,importance,english,mergedCount,note}]}}。</p>
 */
public class CurateResponse {

    private String brief;

    private List<Item> items = new ArrayList<>();

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    /** 单条整理结果。 */
    public static class Item {
        private String title;
        private String link;
        private String sourceName;
        private String category;
        private String summaryZh;
        private int importance;
        private boolean english;
        private int mergedCount;
        private String note;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSummaryZh() {
            return summaryZh;
        }

        public void setSummaryZh(String summaryZh) {
            this.summaryZh = summaryZh;
        }

        public int getImportance() {
            return importance;
        }

        public void setImportance(int importance) {
            this.importance = importance;
        }

        @JSONField(name = "english")
        public boolean isEnglish() {
            return english;
        }

        public void setEnglish(boolean english) {
            this.english = english;
        }

        public int getMergedCount() {
            return mergedCount;
        }

        public void setMergedCount(int mergedCount) {
            this.mergedCount = mergedCount;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    /**
     * 鲁棒解析 LLM 文本为 {@link CurateResponse}。
     *
     * <p>容错：① 剥掉 ```json / ``` 代码围栏；② 截取首个 '{' 到末个 '}' 之间的子串
     * 以容忍模型输出的前后缀杂字；③ 任意异常返回 {@code null}（由上层走降级），不抛出。</p>
     *
     * <p><b>空精选是合法结果</b>：JSON 能解析但 {@code items} 为空（模型判定当天无合格资讯），
     * 返回 items 为空列表的对象，由上层按"宁缺毋滥"处理，<b>不</b>等同于解析失败、<b>不</b>触发降级。
     * 仅当文本根本无法解析为 JSON 对象时才返回 {@code null}。</p>
     *
     * @param raw LLM 原始回复文本，可能为 null
     * @return 解析结果（items 可能为空）；无法解析为 JSON 对象时返回 {@code null}
     */
    public static CurateResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String json = stripFences(raw).trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end < start) {
                return null;
            }
            json = json.substring(start, end + 1);
            CurateResponse resp = JSON.parseObject(json, CurateResponse.class);
            if (resp == null) {
                return null;
            }
            // items 缺失 → 归一化为空列表（合法的"空精选"），不再当作解析失败
            if (resp.getItems() == null) {
                resp.setItems(new ArrayList<>());
            }
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    /** 剥掉 Markdown 代码围栏（```json ... ``` 或 ``` ... ```）。 */
    private static String stripFences(String text) {
        String t = text.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            // 整行都是围栏，无内容
            return t.replace("`", "");
        }
        // 去掉开头 ```xxx 行
        String body = t.substring(firstNl + 1);
        int lastFence = body.lastIndexOf("```");
        if (lastFence >= 0) {
            body = body.substring(0, lastFence);
        }
        return body;
    }
}
