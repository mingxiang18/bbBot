package com.bb.bot.database.aiKeywordAndClue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * ai关键字线索关联表
 * </p>
 *
 * @author misu
 * @since 2024-06-13
 */
@Getter
@Setter
@TableName("ai_keyword_clue")
public class AiKeywordClue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关键字id
     */
    private Long keywordId;

    /**
     * 线索id
     */
    private Long clueId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
