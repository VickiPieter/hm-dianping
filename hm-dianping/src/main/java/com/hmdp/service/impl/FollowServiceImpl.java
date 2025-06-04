package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result followBlogger(Long bloggerId, Boolean isFollow) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 判断是否关注了博主
        if (!isFollow) {
            // 没关注则从数据库删除
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", bloggerId));
        }

        // 关注了则保存到数据库
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(bloggerId);
        save(follow);
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
}
