package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注和取关博主
     * @param bloggerId 博主id
     * @param isFollow 返回true或者false，表示是否关注该博主
     * @return
     */
    Result followBlogger(Long bloggerId, Boolean isFollow);

    /**
     * 关注还是没关注博主
     * @param bloggerId 博主id
     * @return
     */
    Result isFollowBlogger(Long bloggerId);

    /**
     * 共同关注的博主
     * @param followUserId 另一位被你关注的用户id
     * @return 返回共同关注的用户信息
     */
    Result followCommons(Long followUserId);
}
