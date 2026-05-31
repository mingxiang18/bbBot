package com.bb.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 每日资讯日报功能的全局配置（前缀 {@code news}）。
 *
 * <p>对应 application.yml 的 {@code news:} 段。各功能模块（采集 / 整理 / 渲染 / 托管 / 调度）
 * 共享本配置，源列表纯配置化，增删源不改代码。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "news")
public class NewsConfig {

    /** 功能总开关。 */
    private boolean enabled = true;

    /** 定时触发 cron（默认每日 08:00）。 */
    private String cron = "0 0 8 * * ?";

    /** 历史归档保留天数。 */
    private int archiveDays = 30;

    /** 每个源最多采集的条目数。 */
    private int perSourceLimit = 30;

    /** 每个分类在页面上最多展示的条目数。 */
    private int perCategoryLimit = 5;

    private Hosting hosting = new Hosting();
    private Rsshub rsshub = new Rsshub();
    private Ai ai = new Ai();
    private Push push = new Push();
    private List<Source> sources = new ArrayList<>();

    @Data
    public static class Hosting {
        /** HTML 落盘目录（位于 file.path 下）。 */
        private String dir = "/bot/static/news";
        /** 对外访问路径前缀。 */
        private String publicBase = "/news";
        /**
         * 对外可访问的完整基地址（scheme + host[:port]，不含尾斜杠），如 http://1.2.3.4:8099。
         * 用于在聊天里回复可点击的完整链接；留空则只给出相对路径。
         */
        private String externalBaseUrl = "";
    }

    @Data
    public static class Rsshub {
        /** 自建 RSSHub 内网根地址（不含尾部斜杠）。 */
        private String baseUrl = "http://rsshub:1200";
    }

    @Data
    public static class Ai {
        /** 是否启用 AI 整理；关闭则走降级（原始标题 + 原始摘要）。 */
        private boolean enabled = true;
        /** 喂给 LLM 的总条数上限（成本控制）；条目会先按源轮转再截断，保证各源/分类都被 AI 看到。 */
        private int maxItems = 100;
        /** 使用的模型角色：light（廉价总结分类）/ heavy。 */
        private String role = "light";
        /** 是否翻译英文源标题（默认 false：保留英文标题，摘要中文）。 */
        private boolean translateEnTitle = false;
    }

    @Data
    public static class Push {
        /** 首版不自动推送；预留开关。 */
        private boolean enabled = false;
    }

    /** 单个数据源配置。 */
    @Data
    public static class Source {
        /** 源名称，如 "中新网"。 */
        private String name;
        /** 分类键，取值见 NewsCategory。 */
        private String category;
        /** 获取方式："direct"（官方直连）或 "rsshub"（自建 RSSHub 路由）。 */
        private String via;
        /** direct 时的完整 URL。 */
        private String url;
        /** rsshub 时的路由路径（如 "/diandong/news"），与 rsshub.baseUrl 拼接。 */
        private String path;
        /** 语言："zh" 或 "en"。 */
        private String lang = "zh";
    }
}
