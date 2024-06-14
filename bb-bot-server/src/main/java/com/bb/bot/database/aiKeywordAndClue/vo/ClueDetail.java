package com.bb.bot.database.aiKeywordAndClue.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索导入详情
 *
 * @author misu
 * @since 2024-06-12
 */
@Data
public class ClueDetail {

    /**
     * 关键字
     */
    private List<String> keyword;

    /**
     * 线索内容
     */
    private String content;

    /**
     * 权重
     */
    private Double weight;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
