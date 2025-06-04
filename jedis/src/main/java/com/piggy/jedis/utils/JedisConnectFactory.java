package com.piggy.jedis.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @author: piggy
 * date: 2025.05.26 21:53:36
 * version: 1.0
 */
public class JedisConnectFactory {
    private static final JedisPool jedisPool;

    static{
        // jedis线程池配置
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // 最大线程数量
        poolConfig.setMaxTotal(8);
        // 最大空闲数量
        poolConfig.setMaxIdle(8);
        // 最小空闲数量
        poolConfig.setMinIdle(0);
        // 最大等待时长(毫秒)
        poolConfig.setMaxWaitMillis(1000);

        jedisPool = new JedisPool(poolConfig, "192.168.66.4", 6379, 1000);
    }

    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
