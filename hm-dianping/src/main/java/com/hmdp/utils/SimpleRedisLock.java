package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author: piggy
 * @date: 2025/6/1 10:48
 * @version: 1.0
 */

public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private String businessName;
    private static final String REDIS_LOCK_ID = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String businessName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.businessName = businessName;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取当前线程
        String threadId = REDIS_LOCK_ID + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + businessName,
                threadId, timeoutSec, TimeUnit.SECONDS);
        // 自动拆箱或装箱，若是null值，会返回空，而不是true或false；所以这里做一个值的校验，null值同样返回false
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + businessName),
                REDIS_LOCK_ID + Thread.currentThread().getId()
        );
    }

    /*@Override
    public void unlock() {
        // 获取当前线程标识
        String threadId = REDIS_LOCK_ID + Thread.currentThread().getId();

        // redis中存放的锁id
        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + businessName);

        // 判断标识是否一致
        if (threadId.equals(lockId)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + businessName);
        }
    }*/
}
