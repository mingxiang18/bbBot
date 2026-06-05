package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.ImageVisionCache;
import com.bb.bot.database.aiAgent.mapper.ImageVisionCacheMapper;
import com.bb.bot.database.aiAgent.service.IImageVisionCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ImageVisionCacheServiceImpl
        extends ServiceImpl<ImageVisionCacheMapper, ImageVisionCache>
        implements IImageVisionCacheService {

    @Override
    public Optional<String> findDescription(String imageHash) {
        if (StringUtils.isBlank(imageHash)) {
            return Optional.empty();
        }
        ImageVisionCache row = getOne(new LambdaQueryWrapper<ImageVisionCache>()
                .eq(ImageVisionCache::getImageHash, imageHash)
                .last("limit 1"));
        if (row == null || StringUtils.isBlank(row.getDescription())) {
            return Optional.empty();
        }
        try {
            row.setHitCount((row.getHitCount() == null ? 0 : row.getHitCount()) + 1);
            updateById(row);
        } catch (Exception e) {
            log.debug("image_vision_cache hit_count 自增失败（忽略）", e);
        }
        return Optional.of(row.getDescription());
    }

    @Override
    public void put(String imageHash, String description, String model) {
        if (StringUtils.isBlank(imageHash) || StringUtils.isBlank(description)) {
            return;
        }
        ImageVisionCache existing = getOne(new LambdaQueryWrapper<ImageVisionCache>()
                .eq(ImageVisionCache::getImageHash, imageHash)
                .last("limit 1"));
        if (existing != null) {
            existing.setDescription(description);
            existing.setModel(model);
            updateById(existing);
            return;
        }
        ImageVisionCache row = new ImageVisionCache();
        row.setImageHash(imageHash);
        row.setDescription(description);
        row.setModel(model);
        row.setHitCount(0);
        try {
            save(row);
        } catch (Exception e) {
            // 并发下唯一键冲突 → 已有别的线程写入，忽略
            log.debug("image_vision_cache 写入冲突（忽略）hash={}", imageHash, e);
        }
    }
}
