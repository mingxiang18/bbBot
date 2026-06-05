package com.bb.bot.database.aiAgent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.aiAgent.entity.ImageVisionCache;

import java.util.Optional;

public interface IImageVisionCacheService extends IService<ImageVisionCache> {

    /** 按内容哈希取描述；命中则 hit_count+1。 */
    Optional<String> findDescription(String imageHash);

    /** upsert 一条描述缓存。 */
    void put(String imageHash, String description, String model);
}
