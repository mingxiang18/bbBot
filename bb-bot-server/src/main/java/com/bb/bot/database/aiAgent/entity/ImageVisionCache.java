package com.bb.bot.database.aiAgent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片视觉描述缓存：按图片内容哈希（sha256(bytes) 的 hex）缓存视觉模型生成的描述。
 *
 * <p>同一张图只识别一次，跨重启 / 多实例复用；与内存 Caffeine 共同构成两层缓存。
 * image_hash 唯一。</p>
 */
@Data
@TableName("image_vision_cache")
public class ImageVisionCache {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 图片内容 sha256(bytes) 的 hex，唯一键 */
    private String imageHash;

    /** 视觉模型生成的文字描述 */
    private String description;

    /** 出描述用的视觉模型名 */
    private String model;

    /** 缓存命中次数 */
    private Integer hitCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
