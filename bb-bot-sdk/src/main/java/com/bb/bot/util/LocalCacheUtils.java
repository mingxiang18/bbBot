package com.bb.bot.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

/**
 * 本地缓存工具类
 * 使用Caffeine实现
 *
 * @author rym
 */
public class LocalCacheUtils {

    /**
     * Caffeine缓存
     */
    private static final Cache<String, CacheEntity> cache = Caffeine.newBuilder()
            .expireAfter(new CustomExpiry())
            .build();

    /**
     * 设置缓存
     */
    public static void setCacheObject(String key, Object object) {
        cache.put(key, new CacheEntity(object));
    }

    /**
     * 设置缓存和过期时间
     */
    public static void setCacheObject(String key, Object object, long expireTime, TemporalUnit temporalUnit) {
        cache.put(key, new CacheEntity(object, expireTime, temporalUnit));
    }

    /**
     * 获取缓存
     */
    public static <T> T getCacheObject(String key) {
        CacheEntity cacheEntity = cache.getIfPresent(key);
        if (cacheEntity == null) {
            return null;
        } else {
            return (T) cacheEntity.getObject();
        }
    }

    /**
     * 删除缓存
     */
    public static void removeCacheObject(String key) {
        cache.invalidate(key);
    }

    /**
     * 缓存实体类
     */
    @Data
    public static class CacheEntity {
        /**
         * 过期时间
         */
        private long expireTime;
        private TemporalUnit temporalUnit;
        /**
         * 数据实体
         */
        private Object object;

        public CacheEntity() {
        }

        public CacheEntity(Object object) {
            //没有设置过期时间时，设置为10000天过期，也算是永不过期了
            this.expireTime = 10000;
            this.temporalUnit = ChronoUnit.DAYS;
            this.object = object;
        }

        public CacheEntity(Object object, long expireTime, TemporalUnit temporalUnit) {
            this.expireTime = expireTime;
            this.temporalUnit = temporalUnit;
            this.object = object;
        }
    }

    /**
     * 自定义Expiry实现
     */
    private static class CustomExpiry implements com.github.benmanes.caffeine.cache.Expiry<String, CacheEntity> {
        @Override
        public long expireAfterCreate(String key, CacheEntity value, long currentTime) {
            return Duration.of(value.getExpireTime(), value.getTemporalUnit()).toNanos();
        }

        @Override
        public long expireAfterUpdate(String key, CacheEntity value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(String key, CacheEntity value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
