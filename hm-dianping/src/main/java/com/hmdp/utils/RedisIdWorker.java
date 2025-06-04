package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author: piggy
 * @date: 2025/5/31 11:42
 * @version: 1.0
 */

@Component
@RequiredArgsConstructor
public class RedisIdWorker {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 运行开始时间
     */
    private final long START_TIMESTAMP = 1747223100;

    public Long nextId(String keyPrefix){
        // 生成时间戳
        LocalDateTime time = LocalDateTime.now();
        long currentSecond = time.toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentSecond - START_TIMESTAMP;

        // 生成序列号
        // 获取当前年月日
        String date = time.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长
        Long increment = stringRedisTemplate.opsForValue().increment("voucher:" + keyPrefix + date);

        // 拼接返回
        return timestamp << 32 | increment;
    }
}
