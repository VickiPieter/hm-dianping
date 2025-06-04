package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author: piggy
 * @date: 2025/5/28 9:33
 * @version: 1.0
 */

public class LoginIntercept implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断用户是否存在
        if (UserHolder.getUser() == null) {
            // 设置状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 放行
        return true;
    }
}
