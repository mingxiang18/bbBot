package com.bb.bot.database.aiKeywordAndClue.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 线索详情
 *
 * @author misu
 * @since 2024-06-12
 */
@Data
public class ClueDetailVo {

    /**
     * 关键字
     */
    private String keywords;

    /**
     * 线索内容
     */
    private String clueContent;

    /**
     * 权重
     */
    private Double weight;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
