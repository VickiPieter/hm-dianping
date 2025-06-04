package com.piggy.redis;

import com.piggy.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class RedisApplicationTests {
//    @Autowired
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testString(){
        redisTemplate.opsForValue().set("name", "GGBond");
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    @Test
    void testUser(){
        redisTemplate.opsForValue().set("user:1", new User("Rose", 18));
        User user = (User) redisTemplate.opsForValue().get("user:1");
        System.out.println("user = " + user);
    }
}
