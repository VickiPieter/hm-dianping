package com.jedis.test;

import com.piggy.jedis.utils.JedisConnectFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

public class JedisTest {
    private Jedis jedis;
    @BeforeEach
    public void setUp() {
        // 获取连接
        //jedis = new Jedis("192.168.66.4", 6379);
        jedis = JedisConnectFactory.getJedis();
        // 授权访问 (如果有密码的话)
//        jedis.auth("123321");
        // 选择所使用库
        jedis.select(0);
    }

    @Test
    public void testString() {
        // 存取数据
        String set = jedis.set("name", "jack");
        System.out.println("set = " + set);
        // 获取数据
        String name = jedis.get("name");
        System.out.println("name = " + name);
    }

    @AfterEach
    void tearDown() {
        if(jedis != null) {
            // 关闭连接
            jedis.close();
        }
    }
}
