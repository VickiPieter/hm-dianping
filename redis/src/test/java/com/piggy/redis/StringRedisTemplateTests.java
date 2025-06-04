package com.piggy.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggy.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class StringRedisTemplateTests {
//    @Autowired
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString(){
        stringRedisTemplate.opsForValue().set("name", "GGBond");
        Object name = stringRedisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    // 创建ObjectMapper对象
    private static final ObjectMapper mapper = new ObjectMapper();
    @Test
    void testUser() throws JsonProcessingException {
        // 创建User对象
        User user = new User("帝皇铠甲", 1000);
        // 将对象转为字符串
        String valueAsString = mapper.writeValueAsString(user);
        // 发送数据
        stringRedisTemplate.opsForValue().set("user:2", valueAsString);
        // 接受数据返序列化
        String jsonUser = stringRedisTemplate.opsForValue().get("user:2");
        // 将接收到的值在序列化为对象
        User readValue = mapper.readValue(jsonUser, User.class);
        System.out.println("user = " + readValue);
    }
}
