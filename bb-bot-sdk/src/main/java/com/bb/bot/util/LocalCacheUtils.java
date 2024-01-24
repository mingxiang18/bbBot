package com.bb.bot.util;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地缓存工具类
 *
 * @author rym
 */
public class LocalCacheUtils {
    /**
     * 缓存键值对map
     */
    public static Map<String, CacheEntity> cacheMap = new ConcurrentHashMap<>();

    /**
     * 设置缓存
     */
    public static void setCacheObject(String key, Object object) {
        cacheMap.put(key, new CacheEntity(object));
    }

    /**
     * 设置缓存和过期时间
     */
    public static void setCacheObject(String key, Object object, long expireTime, TemporalUnit temporalUnit) {
        cacheMap.put(key, new CacheEntity(LocalDateTime.now().plus(expireTime, temporalUnit), object));
    }

    /**
     * 获取缓存
     */
    public static  <T> T getCacheObject(String key) {
        CacheEntity cacheEntity = cacheMap.get(key);
        if (cacheEntity == null || !cacheEntity.expireTime.isAfter(LocalDateTime.now())) {
            cacheMap.remove(key);
            return null;
        }else {
            return (T) cacheEntity.getObject();
        }
    }

    /**
     * 删除缓存
     */
    public static void removeCacheObject(String key) {
        cacheMap.remove(key);
    }

    /**
     * 缓存实体类
     */
    @Data
    public static class CacheEntity {
        /**
         * 过期时间
         */
        private LocalDateTime expireTime;
        /**
         * 数据实体
         */
        private Object object;

        public CacheEntity() {
        }

        public CacheEntity(Object object) {
            this.expireTime = LocalDateTime.now().withYear(2099);
            this.object = object;
        }

        public CacheEntity(LocalDateTime expireTime, Object object) {
            this.expireTime = expireTime;
            this.object = object;
        }
    }
}
