package com.littlebluenote.chat.controller;

import com.littlebluenote.chat.dto.ReportResolveBody;
import com.littlebluenote.chat.dto.RestrictionBody;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.chat.service.ChatAdminService;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat-admin")
public class ChatAdminController {
    private final ChatAdminService service;

    public ChatAdminController(ChatAdminService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public Result<?> stats(@RequestHeader(Constants.HEADER_USER_ROLE) String role) {
        requireAdmin(role);
        return Result.ok(service.stats());
    }

    @GetMapping("/reports")
    public Result<?> reports(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) String status) {
        requireAdmin(role);
        return Result.ok(service.reports(page, size, status));
    }

    @PostMapping("/reports/{id}/resolve")
    public Result<?> resolve(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                             @RequestHeader(Constants.HEADER_USER_ID) Long adminId,
                             @PathVariable Long id,
                             @Valid @RequestBody ReportResolveBody body) {
        requireAdmin(role);
        return Result.ok(service.resolve(adminId, id, body.resolution()));
    }

    @GetMapping("/restrictions")
    public Result<?> restrictions(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return Result.ok(service.restrictions(page, size));
    }

    @PostMapping("/restrictions")
    public Result<?> restrict(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                              @RequestHeader(Constants.HEADER_USER_ID) Long adminId,
                              @Valid @RequestBody RestrictionBody body) {
        requireAdmin(role);
        return Result.ok(service.restrict(adminId, body));
    }

    @DeleteMapping("/restrictions/{id}")
    public Result<?> lift(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                          @PathVariable Long id) {
        requireAdmin(role);
        service.lift(id);
        return Result.ok();
    }

    @GetMapping("/outbox")
    public Result<?> outbox(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(required = false) String status) {
        requireAdmin(role);
        return Result.ok(service.outbox(page, size, status));
    }

    @PostMapping("/outbox/{id}/retry")
    public Result<?> retryOutbox(@RequestHeader(Constants.HEADER_USER_ROLE) String role,
                                 @PathVariable Long id) {
        requireAdmin(role);
        return Result.ok(service.retryOutbox(id));
    }

    private void requireAdmin(String role) {
        if (!Constants.ROLE_ADMIN.equals(role)) throw BusinessException.forbidden("Admin access required");
    }
}
