package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 判断手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 如果手机号不合法，则打回，提示用户
            return Result.fail("手机号格式有误!");
        }

        // 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码到redis中
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 模拟发送验证码，使用阿里等过于复杂
        log.debug("发送验证码成功，验证码为{}", code);

        // 返回请求成功数据
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 获取手机号
        String phone = loginForm.getPhone();

        // 校验手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            // 不合法直接返回错误信息
            return Result.fail("手机号格式有误!");
        }

        // 合法，在校验验证码
        String cacheCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码有误!");
        }

        // 判断数据库是否有该手机号信息
        User user = query().eq(LOGIN_SESSION_PHONE, phone).one();

        // 如果用户不存在，则直接作为新用户注册
        if (user == null) {
            // 创建新用户
            user = createUserWithCode(phone);
            // 将用户信息保存到数据库，否则信息不能传递到session中，无法获取userId
            save(user);
        }

        // 将用户信息保存到redis中
        // 随机生成token，作为登录令牌
        String token = UUID.randomUUID(true).toString(true);
        // 将user对象转为userDTO存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将userDTO信息存储到redis的HashMap中
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        // 存储
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 设置token有效期
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 返回给前端token
        return Result.ok(token);
    }

    private User createUserWithCode(String phone) {
        // 创建新用户
        User user = new User();

        // 给用户赋值
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));

        return user;
    }
}
