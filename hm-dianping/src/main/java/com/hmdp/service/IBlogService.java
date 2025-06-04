package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门的博客展示
     * @param current 查询参数，默认为1
     * @return 返回热门博客信息展示
     */
    Result queryHotBlog(Integer current);

    /**
     * 通过博客id查询博客的信息
     * @param id 博客的id
     * @return 博客详情信息
     */
    Result queryBlogById(Long id);

    /**
     * 修改blog的点赞数量
     * @param id 博客id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 用户点赞喜欢的博客
     * @param id 博客id
     * @return
     */
    Result queryUserLikesOfBlog(Long id);
}
