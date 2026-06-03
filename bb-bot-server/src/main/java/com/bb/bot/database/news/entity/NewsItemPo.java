package com.bb.bot.database.news.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日资讯条目持久化对象，对应表 {@code news_item}。
 *
 * <p>承载原始采集字段（report_date / source_name / category / title / link /
 * link_hash / description / pub_date / lang）与 AI 整理回写字段
 * （summary_zh / importance / cluster_key / merged_count）。</p>
 *
 * @author rym
 * @since 2026-05-30
 */
@Data
@TableName("news_item")
public class NewsItemPo {

    private static final long serialVersionUID = 1L;

    /** 自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属日报日期。 */
    private LocalDate reportDate;

    /** 源名称。 */
    private String sourceName;

    /** 最终分类键。 */
    private String category;

    /** 标题（英文源保留英文）。 */
    private String title;

    /** 逐篇文章真实 URL。 */
    private String link;

    /** link 归一化后的 SHA1，跨天去重键。 */
    private String linkHash;

    /** 原始摘要。 */
    private String description;

    /** 原始发布时间字符串。 */
    private String pubDate;

    /** 语言 zh/en。 */
    private String lang;

    /** AI 中文摘要。 */
    private String summaryZh;

    /** 重要性 1-5。 */
    private Integer importance;

    /** AI 语义聚类键。 */
    private String clusterKey;

    /** 多源合并条目数。 */
    private Integer mergedCount;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    // ---- Phase 2：候选生命周期 ----

    /** 标准化发布时间（由 pub_date 解析，解析失败为空）。 */
    private LocalDateTime publishedAt;

    /** 首次采集时间。 */
    private LocalDateTime firstSeenAt;

    /** 最近一次仍在源 feed 中出现的时间。 */
    private LocalDateTime lastSeenAt;

    /** 评估状态：RAW / SELECTED / REJECTED（见 NewsReviewState）。 */
    private String reviewState;

    /** 拒绝原因：duplicate/stale/low_value/invalid/ai_omitted 等。 */
    private String rejectReason;

    /** 被哪天日报选中（展示用查询键，区别于 report_date=采集日）。 */
    private LocalDate selectedReportDate;
}
