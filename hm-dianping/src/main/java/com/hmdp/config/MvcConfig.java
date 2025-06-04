package com.hmdp.config;

import com.hmdp.utils.LoginIntercept;
import com.hmdp.utils.RefreshIntercept;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author: piggy
 * @date: 2025/5/28 9:41
 * @version: 1.0
 */

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册mvc拦截器
        registry.addInterceptor(new LoginIntercept())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/like/{id}",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/voucher-order/**"
                ).order(1);

        registry.addInterceptor(new RefreshIntercept(stringRedisTemplate)).addPathPatterns("/**")
                .order(0);
    }
}
