package com.littlebluenote.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import com.littlebluenote.common.dto.UserDTO;
import com.littlebluenote.user.entity.User;
import com.littlebluenote.user.service.UserBizService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User profile + follow endpoints. Also exposes internal batch endpoints used by
 * other services through OpenFeign (see UserClient in lbn-recommend-service).
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserBizService svc;

    public UserController(UserBizService svc) {
        this.svc = svc;
    }

    @GetMapping("/me")
    public Result<UserDTO> me(@RequestHeader(Constants.HEADER_USER_ID) Long uid) {
        return Result.ok(svc.getUser(uid));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> get(@PathVariable Long id) {
        return Result.ok(svc.getUser(id));
    }

    /** internal: batch fetch used by recommend/post services via Feign */
    @GetMapping("/batch")
    public Result<List<UserDTO>> batch(@RequestParam("ids") String ids) {
        List<Long> list = Arrays.stream(ids.split(","))
                .filter(s -> !s.isBlank()).map(Long::valueOf).collect(Collectors.toList());
        return Result.ok(svc.getUsers(list));
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String keyword) {
        IPage<User> p = svc.pageUsers(page, size, keyword);
        Map<String, Object> m = new HashMap<>();
        m.put("total", p.getTotal());
        m.put("records", p.getRecords().stream().map(svc::toDTO).toList());
        return Result.ok(m);
    }

    @PutMapping("/me")
    public Result<?> update(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                            @RequestBody User patch) {
        svc.updateProfile(uid, patch);
        return Result.ok();
    }

    @PostMapping("/follow/{followee}")
    public Result<?> follow(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                            @PathVariable Long followee) {
        return Result.ok(svc.follow(uid, followee));
    }

    @DeleteMapping("/follow/{followee}")
    public Result<?> unfollow(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                              @PathVariable Long followee) {
        svc.unfollow(uid, followee);
        return Result.ok();
    }

    @GetMapping("/following")
    public Result<List<Long>> following(@RequestHeader(Constants.HEADER_USER_ID) Long uid) {
        return Result.ok(svc.following(uid));
    }

    @GetMapping("/{id}/isFollowing")
    public Result<Boolean> isFollowing(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                       @PathVariable Long id) {
        return Result.ok(svc.isFollowing(uid, id));
    }
}
