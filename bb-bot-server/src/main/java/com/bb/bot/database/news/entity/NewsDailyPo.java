package com.bb.bot.database.news.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日资讯日报元信息持久化对象，对应表 {@code news_daily}。
 *
 * <p>主键即 {@code report_date}（业务键写入，非自增）。</p>
 *
 * @author rym
 * @since 2026-05-30
 */
@Data
@TableName("news_daily")
public class NewsDailyPo {

    private static final long serialVersionUID = 1L;

    /** 日报日期，主键（业务键写入）。 */
    @TableId(value = "report_date", type = IdType.INPUT)
    private LocalDate reportDate;

    /** 今日速览导语。 */
    private String brief;

    /** 精选条数。 */
    private Integer totalCount;

    /** 聚合源数。 */
    private Integer sourceCount;

    /** HTML 落盘相对路径。 */
    private String htmlPath;

    /** 生成时间。 */
    private LocalDateTime generatedAt;
}
