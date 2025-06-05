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
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.LOGIN_SESSION_PHONE;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    private final StringRedisTemplate stringRedisTemplate;

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
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

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
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
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
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        // 设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 返回给前端token
        return Result.ok(token);
    }

    @Override
    public Result signIn() {
        // 1. 获取当前登录用户的id
        Long userId = UserHolder.getUser().getId();

        // 2. 获取当前日期
        LocalDateTime dateTime = LocalDateTime.now();

        // 3. 获取当前日期是本月的第几天
        int dayOfMonth = dateTime.getDayOfMonth() - 1;

        // 4. 发送存储到redis中
        String format = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String key = USER_SIGN_KEY + userId + ":" + format;
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth, true);

        // 5. 返回
        return Result.ok();
    }

    @Override
    public Result signInCount() {
        // 1. 获取当前登录用户的id
        Long userId = UserHolder.getUser().getId();

        // 2. 获取当前日期
        LocalDateTime dateTime = LocalDateTime.now();

        // 3. 获取当前日期是本月的第几天
        int dayOfMonth = dateTime.getDayOfMonth();

        // 4. 从redis中取出数据
        String format = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String key = USER_SIGN_KEY + userId + ":" + format;
        List<Long> bitted = stringRedisTemplate.opsForValue().
                bitField(key, BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        // 5. 对bitted做非空判断
        if (bitted == null || bitted.isEmpty()) {
            // 说明没有签到
            return Result.ok(0);
        }
        // 6. 获取当前月份的二进制bit位
        Long num = bitted.get(0);
        // 6.1 判断num是否为0或者null
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.2 遍历该二进制位，如果是0则表示未签到，如果是1则表示已签到
        int count = 0;
        // 6.3 让该数与1做与运算，得到该数的最后一个bit位，判断是否为0
        while ((num & 1) != 0) {
            // 6.4 不为0，计数器+1，统计签到的次数
            count++;
            // 6.5 向右移动一位，抛弃掉最后一位bit位，继续下一位二进制数的与运算
            num >>>= 1;
        }
        // 返回
        return Result.ok(count);
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
