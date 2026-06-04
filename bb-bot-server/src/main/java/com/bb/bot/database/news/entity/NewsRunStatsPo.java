package com.bb.bot.database.news.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一次日报生成的运行指标历史（持久化版的 {@code NewsRunStats}）。
 *
 * <p>每次 {@code DailyNewsSchedule.stats(...)} 落一行，使「今天筛了几条 / AI 是否降级 /
 * 最近几次生成情况」可被 owner 通过 {@code news_health} 工具回溯，而不再只活在日志里。</p>
 *
 * @author rym
 */
@Data
@TableName("news_run_stats")
public class NewsRunStatsPo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private LocalDate reportDate;

    /** 采集总数。 */
    private Integer fetched;

    /** 本次新增（去重后）。 */
    private Integer fresh;

    /** 候选池数量。 */
    private Integer eligible;

    /** 最终展示数量。 */
    private Integer selected;

    /** AI 状态：success / empty / fallback / disabled / no_fetch / no_candidate。 */
    private String aiStatus;

    /** 是否实际出页。 */
    private Boolean published;

    /** 出页访问地址，未出页为 null。 */
    private String url;

    private LocalDateTime createdAt;
}
