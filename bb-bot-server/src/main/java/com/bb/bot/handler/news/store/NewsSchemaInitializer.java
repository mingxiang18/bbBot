package com.bb.bot.handler.news.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 启动时幂等确保 news 两张表存在并补齐 Phase 2 候选生命周期列。
 *
 * <p>参照 {@link com.bb.bot.aiAgent.auth.AiAgentSchemaInitializer} 的"拉起即用"模式：
 * {@code CREATE TABLE IF NOT EXISTS} + 逐句 {@code ALTER TABLE ADD COLUMN}，列已存在时
 * 该句报错被逐句 try/catch 吞掉。</p>
 *
 * <p><b>为什么需要它</b>：bbBot 不用 Flyway/Liquibase，而 push master 即自动部署到生产；
 * 若先合入读新列的代码、生产库却没加列，新 pod 起来就 Unknown column → rollout 失败。
 * 把迁移做成随启动幂等执行，从根上消除这个部署顺序风险。</p>
 */
@Slf4j
@Component
public class NewsSchemaInitializer {

    @Autowired
    private DataSource dataSource;

    /** 与 aiAgent.autoCreateTables 对齐：默认开启，可关。 */
    @Value("${news.autoCreateTables:true}")
    private boolean autoCreateTables;

    private static final String[] DDL = new String[] {
            // 1) 基表（与 sql/news_ddl.sql 一致，保证全新环境拉起即用）
            "CREATE TABLE IF NOT EXISTS news_item (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键'," +
                    "  report_date DATE NOT NULL COMMENT '所属日报日期(采集日)'," +
                    "  source_name VARCHAR(64) NOT NULL COMMENT '源名称'," +
                    "  category VARCHAR(16) DEFAULT NULL COMMENT '最终分类键'," +
                    "  title VARCHAR(512) NOT NULL COMMENT '标题'," +
                    "  link VARCHAR(1024) NOT NULL COMMENT '逐篇文章真实 URL'," +
                    "  link_hash CHAR(40) NOT NULL COMMENT 'link 归一化 SHA1 去重键'," +
                    "  description TEXT COMMENT '原始摘要'," +
                    "  pub_date VARCHAR(64) DEFAULT NULL COMMENT '原始发布时间字符串'," +
                    "  lang VARCHAR(8) DEFAULT 'zh' COMMENT '语言 zh/en'," +
                    "  summary_zh VARCHAR(512) DEFAULT NULL COMMENT 'AI 中文摘要'," +
                    "  importance TINYINT DEFAULT NULL COMMENT '重要性 1-5'," +
                    "  cluster_key VARCHAR(64) DEFAULT NULL COMMENT 'AI 语义聚类键'," +
                    "  merged_count INT DEFAULT 1 COMMENT '多源合并条目数'," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "  PRIMARY KEY (id)," +
                    "  UNIQUE KEY uk_link_hash (link_hash)," +
                    "  KEY idx_date (report_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日资讯条目'",

            "CREATE TABLE IF NOT EXISTS news_daily (" +
                    "  report_date DATE NOT NULL COMMENT '日报日期，主键'," +
                    "  brief TEXT COMMENT '今日速览导语'," +
                    "  total_count INT DEFAULT 0 COMMENT '精选条数'," +
                    "  source_count INT DEFAULT 0 COMMENT '聚合源数'," +
                    "  html_path VARCHAR(256) DEFAULT NULL COMMENT 'HTML 落盘相对路径'," +
                    "  generated_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间'," +
                    "  PRIMARY KEY (report_date)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日资讯日报元信息'",

            // 2) Phase 2 候选生命周期列（已存在则该句被吞）
            "ALTER TABLE news_item ADD COLUMN published_at DATETIME DEFAULT NULL COMMENT '标准化发布时间'",
            "ALTER TABLE news_item ADD COLUMN first_seen_at DATETIME DEFAULT NULL COMMENT '首次采集时间'",
            "ALTER TABLE news_item ADD COLUMN last_seen_at DATETIME DEFAULT NULL COMMENT '最近一次仍在源中出现'",
            "ALTER TABLE news_item ADD COLUMN review_state VARCHAR(16) DEFAULT 'RAW' COMMENT 'RAW/SELECTED/REJECTED'",
            "ALTER TABLE news_item ADD COLUMN reject_reason VARCHAR(32) DEFAULT NULL COMMENT '拒绝原因'",
            "ALTER TABLE news_item ADD COLUMN selected_report_date DATE DEFAULT NULL COMMENT '被哪天日报选中'",

            // 3) 候选池查询索引（已存在则被吞）
            "ALTER TABLE news_item ADD KEY idx_state_seen (review_state, first_seen_at)",
            "ALTER TABLE news_item ADD KEY idx_selected_date (selected_report_date)",

            // 4) 历史回填：迁移前已整理(summary_zh 非空)的行回填 selected_report_date=report_date、
            //    review_state=SELECTED，使新版 getReport(按 selected_report_date 查) 仍能展示旧日报。
            //    只动 selected_report_date 仍为空的行，幂等。
            "UPDATE news_item SET selected_report_date = report_date, review_state = 'SELECTED' " +
                    "WHERE summary_zh IS NOT NULL AND summary_zh <> '' AND selected_report_date IS NULL",

            // 5) 可观测性：源健康记录（每次每源一行）+ 运行指标历史（每次生成一行）
            "CREATE TABLE IF NOT EXISTS news_source_health (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键'," +
                    "  report_date DATE DEFAULT NULL COMMENT '抓取所属日期'," +
                    "  source_name VARCHAR(64) NOT NULL COMMENT '源名称'," +
                    "  category VARCHAR(16) DEFAULT NULL COMMENT '分类键'," +
                    "  via VARCHAR(16) DEFAULT NULL COMMENT '获取方式 direct/rsshub'," +
                    "  status VARCHAR(16) NOT NULL COMMENT 'ok/empty/timeout/connect/http/parse/error'," +
                    "  item_count INT DEFAULT 0 COMMENT '本次抓取条数'," +
                    "  error_type VARCHAR(16) DEFAULT NULL COMMENT '失败类型'," +
                    "  error_msg VARCHAR(512) DEFAULT NULL COMMENT '失败信息'," +
                    "  cost_ms BIGINT DEFAULT NULL COMMENT '抓取耗时(ms)'," +
                    "  checked_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间'," +
                    "  PRIMARY KEY (id)," +
                    "  KEY idx_source_time (source_name, checked_at)," +
                    "  KEY idx_checked (checked_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯源抓取健康记录'",

            "CREATE TABLE IF NOT EXISTS news_run_stats (" +
                    "  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键'," +
                    "  report_date DATE DEFAULT NULL COMMENT '日报日期'," +
                    "  fetched INT DEFAULT 0 COMMENT '采集总数'," +
                    "  fresh INT DEFAULT 0 COMMENT '本次新增'," +
                    "  eligible INT DEFAULT 0 COMMENT '候选池数量'," +
                    "  selected INT DEFAULT 0 COMMENT '最终展示数量'," +
                    "  ai_status VARCHAR(24) DEFAULT NULL COMMENT 'AI 状态'," +
                    "  published TINYINT DEFAULT 0 COMMENT '是否出页'," +
                    "  url VARCHAR(256) DEFAULT NULL COMMENT '出页地址'," +
                    "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间'," +
                    "  PRIMARY KEY (id)," +
                    "  KEY idx_created (created_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日报运行指标历史'"
    };

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextRefreshed(ContextRefreshedEvent event) {
        if (!autoCreateTables) {
            log.info("news.autoCreateTables=false，跳过 news 表初始化");
            return;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : DDL) {
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    log.debug("news schema 语句跳过（多为列/索引已存在）: {}", e.getMessage());
                }
            }
            log.info("news schema 检查 / 迁移完成");
        } catch (Exception e) {
            log.error("news schema 初始化失败，日报候选生命周期可能不可用", e);
        }
    }
}
