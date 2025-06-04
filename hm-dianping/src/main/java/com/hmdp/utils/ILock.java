package com.hmdp.utils;

/**
 * @author: piggy
 * @date: 2025/6/1 10:47
 * @version: 1.0
 */

public interface ILock {

    /**
     * 尝试获取分布式锁
     * @param timeoutSec 锁的有效期时间
     * @return true获取锁成功，false获取或失败
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
