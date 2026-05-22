package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型单价（按百万 token）。一行对应一个 (provider, model)。
 * source：seed=预置 / manual=管理员命令 / litellm=定时拉取（不覆盖前两者）。
 */
@Data
@TableName("ai_model_pricing")
public class AiModelPricing {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String providerName;
    private String model;

    /** 该单价的币种：CNY / USD。结算时换算成 CNY。 */
    private String currency;

    /** 输入（prompt，cache-miss）单价，元或美元/百万 token。 */
    private BigDecimal inputPerMillion;

    /** 输出（completion）单价/百万 token。 */
    private BigDecimal outputPerMillion;

    /** 命中缓存（cache read）的输入单价/百万 token；null 表示无分级，按 inputPerMillion 计。 */
    private BigDecimal cacheHitInputPerMillion;

    /** 写缓存（cache creation）的输入单价/百万 token；仅 Anthropic 收费，null/0 表示不收。 */
    private BigDecimal cacheWriteInputPerMillion;

    /** seed / manual / litellm */
    private String source;

    private LocalDateTime updatedAt;
}
