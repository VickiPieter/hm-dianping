package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollPageResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@RequiredArgsConstructor
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private final IUserService userService;

    private final StringRedisTemplate stringRedisTemplate;

    private final IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isCurrentUserIfLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询博客信息
        Blog blog = getById(id);
        if (blog == null) {
            // 博客不存在
            return Result.fail("博客不存在!");
        }

        // 查询用户信息
        queryBlogUser(blog);

        // 判断当前用户是否喜欢
        isCurrentUserIfLiked(blog);

        return Result.ok(blog);
    }

    private void isCurrentUserIfLiked(Blog blog) {
        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        // 判断用户是否存在
        if (user == null) {
            // 不存在，则不查询该用户是否点赞过
            return;
        }
        Long userId = user.getId();

        // 判断当前用户是否给博客点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());

        // 若isMember是true则将isLiked修改为true
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        // 获取当前用户id
        Long userId = UserHolder.getUser().getId();

        // 判断当前用户是否给博客点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());

        // 若没有点赞
        if (score == null) {
            // 修改点赞数 liked += 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 保存到redis中
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        }else {
            // 若已经点过赞
            // 修改点赞数 liked -= 1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 从redis中一处当前用户id
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryUserLikesOfBlog(Long id) {
        // 查询前五为点赞的用户
        Set<String> idSet = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        // 判断idSet是否为空或者null
        if (idSet.isEmpty() || idSet == null) {
            return Result.ok(Collections.emptyList());
        }

        // 解析用户id
        List<Long> idList = idSet.stream().map(Long::valueOf).collect(Collectors.toList());
        // 将idList转为String类型
        String idStr = StrUtil.join(",", idList);
        // 根据用户id查询用户
        List<UserDTO> userDTOS = userService.query().in("id", idList).last("ORDER BY FIELD(id, " + idStr + ")")
                .list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 返回用户
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        // 判断是否保存成功
        if (!isSuccess) {
            return Result.fail("保存博客失败,请稍后重试!");
        }
        // 获取博主的所有粉丝
        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();

        // 给博主的所有粉丝推送博主所发的博客
        for (Follow follow : followUserId) {
            // 获取所有粉丝的id
            Long funId = follow.getUserId();
            // 推送博客给粉丝
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + funId, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long maxTime, Integer offset) {
        // 1.获取当前用户的id
        Long userId = UserHolder.getUser().getId();

        // 2.去查询博主发给你的邮件(博客)，即查询自己的收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(FEED_KEY + userId, 0, maxTime, offset, 3);
        // 3.判断集合是否非空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 4.解析数据
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        // 最小时间戳
        long minTimestamp = 0;
        // 偏移量
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            // 4.1获取的是博客id（所关注博主的），添加进集合中
            blogIds.add(Long.valueOf(typedTuple.getValue()));
            // 4.2获取时间戳
            long queryTimestamp = typedTuple.getScore().longValue();
            if (queryTimestamp == minTimestamp) {
                os++;
            }else {
                minTimestamp = queryTimestamp;
                os = 1;
            }
        }
        // 5.根据获取的blogIds来查询博客
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query().in("id", blogIds).
                last("ORDER BY FIELD(id, " + idStr + ")").list();

        for (Blog blog : blogs) {
            // 查询用户信息
            queryBlogUser(blog);

            // 判断当前用户是否喜欢
            isCurrentUserIfLiked(blog);
        }

        // 5.封装数据
        ScrollPageResult pageResult = new ScrollPageResult();
        pageResult.setList(blogs);
        pageResult.setMinTime(minTimestamp);
        pageResult.setOffset(os);

        // 5.返回
        return Result.ok(pageResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }
}
