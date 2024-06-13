package com.bb.bot.database.aiKeywordAndClue.vo;

import lombok.Data;

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
}
