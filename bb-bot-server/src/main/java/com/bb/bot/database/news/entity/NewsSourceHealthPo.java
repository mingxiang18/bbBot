package com.bb.bot.database.news.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单个资讯源单次抓取的健康记录，用于回答「哪些源失效了 / 某源最近抓取情况」。
 *
 * <p>每次 {@code fetchAll} 对每个源各落一行。status 见 {@code NewsFetcherImpl.classifyError}：
 * ok（有内容）/ empty（连通但 0 条，疑似停更）/ timeout / connect / http / parse / error。</p>
 *
 * @author rym
 */
@Data
@TableName("news_source_health")
public class NewsSourceHealthPo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 抓取所属日期（采集日），便于按天聚合「连续失败」。 */
    private LocalDate reportDate;

    private String sourceName;

    private String category;

    /** 获取方式 direct / rsshub。 */
    private String via;

    /** ok / empty / timeout / connect / http / parse / error。 */
    private String status;

    /** 本次抓取条数（成功时 >0，empty/失败为 0）。 */
    private Integer itemCount;

    /** 失败时的错误类型（与 status 一致，冗余便于查询）；成功为 null。 */
    private String errorType;

    /** 失败时的简短错误信息。 */
    private String errorMsg;

    /** 本次抓取耗时（毫秒）。 */
    private Long costMs;

    private LocalDateTime checkedAt;
}
