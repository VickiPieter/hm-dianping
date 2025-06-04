package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result userFollowBlogger(@PathVariable("id") Long bloggerId, @PathVariable("isFollow") Boolean isFollow){
        return followService.followBlogger(bloggerId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowBlogger(@PathVariable("id") Long bloggerId){
        return followService.isFollowBlogger(bloggerId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long followUserId){
        return followService.followCommons(followUserId);
    }
}
