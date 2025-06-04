package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: piggy
 * @date: 2025/6/1 16:56
 * @version: 1.0
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        // 创建配置文件
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.66.4:6379");

        // 返回RedissonClient对象
        return Redisson.create(config);
    }
}
