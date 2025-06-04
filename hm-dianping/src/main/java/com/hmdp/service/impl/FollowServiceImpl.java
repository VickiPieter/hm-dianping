package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;

    private final IUserService userService;

    @Override
    public Result followBlogger(Long bloggerId, Boolean isFollow) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // redis存储关注的key
        String key = "follows:" + userId;
        // 判断是否关注了博主
        if (!isFollow) {
            // 没关注则从数据库删除
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", bloggerId));
            // 判断是否删除成功
            if (isSuccess) {
                // 从redis中将关注的followUserId删除
                stringRedisTemplate.opsForSet().remove(key, bloggerId.toString());
            }
            return Result.ok();
        }

        // 关注了则保存到数据库
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(bloggerId);
        boolean isSuccess = save(follow);
        // 判断是否保存成功
        if (isSuccess) {
            // 将关注的followUserId保存到redistribution中
            stringRedisTemplate.opsForSet().add(key, bloggerId.toString());
        }
        return Result.ok(follow);
    }

    @Override
    public Result isFollowBlogger(Long bloggerId) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 从数据库查询用户是否关注了该博主
        Integer count = query().eq("user_id", userId).eq("follow_user_id", bloggerId).count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long followUserId) {
        // 获取当前登录用户的id
        Long userId = UserHolder.getUser().getId();

        String key1 = "follows:" + userId;
        String key2 = "follows:" + followUserId;

        // 共同交集的集合
        Set<String> intersectUserId = stringRedisTemplate.opsForSet().intersect(key1, key2);
        // 判断是否有共同集合，没有则返回空集合
        if (intersectUserId == null || intersectUserId.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析数据
        List<Long> userList = intersectUserId.stream().map(Long::valueOf).collect(Collectors.toList());
        // 查询用户信息
        List<UserDTO> userDTOS = userService.listByIds(userList).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 返回
        return Result.ok(userDTOS);
    }
}
