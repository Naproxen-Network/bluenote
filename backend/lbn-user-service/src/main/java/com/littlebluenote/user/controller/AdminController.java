package com.littlebluenote.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import com.littlebluenote.user.entity.User;
import com.littlebluenote.user.service.UserBizService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin console endpoints. The gateway guarantees only ADMIN-role tokens reach here,
 * but we double-check the role header for defence in depth.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserBizService svc;

    public AdminController(UserBizService svc) {
        this.svc = svc;
    }

    private void requireAdmin(String role) {
        if (!Constants.ROLE_ADMIN.equals(role)) {
            throw new SecurityException("Admin access required");
        }
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> users(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword) {
        requireAdmin(role);
        IPage<User> p = svc.pageUsers(page, size, keyword);
        Map<String, Object> m = new HashMap<>();
        m.put("total", p.getTotal());
        m.put("records", p.getRecords().stream().map(svc::toDTO).toList());
        return Result.ok(m);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestHeader(Constants.HEADER_USER_ROLE) String role) {
        requireAdmin(role);
        return Result.ok(svc.stats());
    }

    @ExceptionHandler(SecurityException.class)
    public Result<?> handle(SecurityException e) {
        return Result.error(403, e.getMessage());
    }
}
