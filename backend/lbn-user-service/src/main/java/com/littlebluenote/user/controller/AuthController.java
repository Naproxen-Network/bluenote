package com.littlebluenote.user.controller;

import com.littlebluenote.common.Result;
import com.littlebluenote.user.entity.User;
import com.littlebluenote.user.service.UserBizService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserBizService svc;

    public AuthController(UserBizService svc) {
        this.svc = svc;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            return Result.ok(svc.login(body.get("username"), body.get("password")));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/admin/login")
    public Result<Map<String, Object>> adminLogin(@RequestBody Map<String, String> body) {
        try {
            return Result.ok(svc.adminLogin(body.get("username"), body.get("password")));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody User u) {
        try {
            return Result.ok(svc.register(u));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
