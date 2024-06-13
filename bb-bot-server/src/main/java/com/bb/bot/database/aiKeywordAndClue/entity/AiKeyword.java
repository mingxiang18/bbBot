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
 * ai关键字表
 * </p>
 *
 * @author misu
 * @since 2024-06-12
 */
@Getter
@Setter
@TableName("ai_keyword")
public class AiKeyword implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 群聊或频道id
     */
    private String groupId;

    /**
     * 关键字值
     */
    private String keyName;

    /**
     * 权重
     */
    private Double weight;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
