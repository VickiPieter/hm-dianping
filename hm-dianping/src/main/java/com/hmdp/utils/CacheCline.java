package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author: piggy
 * @date: 2025/5/29 22:30
 * @version: 1.0
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheCline {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String mainKey, String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForHash().put(mainKey, key, JSONUtil.toJsonStr(value));
        stringRedisTemplate.expire(mainKey, time, unit);
    }

    public void setLogicExpire(String mainKey, String key, Object value, Long time, TimeUnit unit){
        // 设置过期时长
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 存储到redis中
        stringRedisTemplate.opsForHash().put(mainKey, key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, String key, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 从redis中查询
        String json = (String) stringRedisTemplate.opsForHash().get(keyPrefix + id, key);

        // 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在，则返回
            return JSONUtil.toBean(json, type);
        }

        // 判断数据是否是空值
        if (json != null) {
            return null;
        }

        // 不存在，则去数据库查询
        R r = dbFallback.apply(id);

        // 数据库中不存在，返回错误
        if (r == null) {
            // 将null值写入redis作为缓存
            stringRedisTemplate.opsForHash().put(keyPrefix + id, key, "");
            stringRedisTemplate.expire(keyPrefix + id, time, unit);

            // 返回错误信息
            return null;
        }

        // 若存在，则写回redis缓存，在返回商铺信息
        this.set(keyPrefix + id, key, r, time, unit);

        // 返回店铺信息
        return r;
    }

    public <R, ID> R queryWithLogicExpire(
            String keyPrefix, String key, ID id, Class<R> type, Long time,
            TimeUnit unit, Function<ID, R> dbFallback, String lockPrefix) {
        // 从redis中查询
        String json = (String) stringRedisTemplate.opsForHash().get(keyPrefix + id, key);

        // 判断是否命中数据
        if (StrUtil.isBlank(json)) {
            // 缓存未命中，从数据库查询
            R r = dbFallback.apply(id);
            if (r == null) {
                // 数据库也没有，确实不存在
                return null;
            }

            // 数据库有数据，写入缓存并设置逻辑过期时间
            this.setLogicExpire(keyPrefix + id, key, r, time, unit);
            return r;
        }

        // 命中，将shopCache反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断逻辑时间是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }

        // 过期，进行缓存重建
        // 获取互斥锁
        String lockKey = lockPrefix + id;
        Boolean isLock = tryLock(lockKey, time, unit);

        // 判断是否获得互斥锁
        if (isLock) {
            // 判断逻辑时间是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期，直接返回店铺信息
                return r;
            } else {
                // 开启独立线程去处理缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 从数据库获取数据
                        R r1 = dbFallback.apply(id);
                        // 重建缓存
                        this.setLogicExpire(keyPrefix + id, key, r1, time, unit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        // 释放锁
                        unlock(lockKey);
                    }
                });
            }
        }

        // 返回过时的店铺信息
        return r;
    }

    /**
     * 尝试获取互斥锁，解决多线程并发请求
     *
     * @param key 用于后续释放
     * @return Boolean值
     */
    private Boolean tryLock(String key, Long time, TimeUnit unit) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", time, unit);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
