-- 每日资讯日报功能建表脚本
-- 说明：项目未使用 Flyway/Liquibase 自动迁移，本脚本需在 MySQL 中手动执行。
-- 字符集与现有库保持一致（utf8mb4）。

-- 原始 + 整理后条目（一行一条）
CREATE TABLE IF NOT EXISTS news_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    report_date  DATE         NOT NULL COMMENT '所属日报日期',
    source_name  VARCHAR(64)  NOT NULL COMMENT '源名称',
    category     VARCHAR(16)            DEFAULT NULL COMMENT '最终分类键',
    title        VARCHAR(512) NOT NULL COMMENT '标题（英文源保留英文）',
    link         VARCHAR(1024) NOT NULL COMMENT '逐篇文章真实 URL',
    link_hash    CHAR(40)     NOT NULL COMMENT 'link 归一化后的 SHA1，跨天去重键',
    description  TEXT                  COMMENT '原始摘要',
    pub_date     VARCHAR(64)           DEFAULT NULL COMMENT '原始发布时间字符串',
    lang         VARCHAR(8)            DEFAULT 'zh' COMMENT '语言 zh/en',
    summary_zh   VARCHAR(512)          DEFAULT NULL COMMENT 'AI 中文摘要',
    importance   TINYINT               DEFAULT NULL COMMENT '重要性 1-5',
    cluster_key  VARCHAR(64)           DEFAULT NULL COMMENT 'AI 语义聚类键',
    merged_count INT                   DEFAULT 1 COMMENT '多源合并条目数',
    created_at   DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_link_hash (link_hash),
    KEY idx_date (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日资讯条目';

-- 每日报告元信息（速览 + 统计 + 落盘路径）
CREATE TABLE IF NOT EXISTS news_daily (
    report_date  DATE         NOT NULL COMMENT '日报日期，主键',
    brief        TEXT                  COMMENT '今日速览导语',
    total_count  INT                   DEFAULT 0 COMMENT '精选条数',
    source_count INT                   DEFAULT 0 COMMENT '聚合源数',
    html_path    VARCHAR(256)          DEFAULT NULL COMMENT 'HTML 落盘相对路径',
    generated_at DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日资讯日报元信息';
