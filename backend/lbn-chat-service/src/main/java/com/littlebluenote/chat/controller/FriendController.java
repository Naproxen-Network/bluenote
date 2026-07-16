package com.littlebluenote.chat.controller;

import com.littlebluenote.chat.dto.BlockBody;
import com.littlebluenote.chat.dto.FriendRequestBody;
import com.littlebluenote.chat.service.FriendService;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService service;

    public FriendController(FriendService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    public Result<?> request(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                             @Valid @RequestBody FriendRequestBody body) {
        long targetId = service.resolveTarget(body.targetUserId(), body.targetUsername());
        return Result.ok(service.request(userId, targetId, body.message()));
    }

    @GetMapping("/lookup")
    public Result<?> lookup(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @RequestParam String identity) {
        return Result.ok(service.lookupTarget(userId, identity));
    }

    @GetMapping("/requests/incoming")
    public Result<?> incoming(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(service.incoming(userId));
    }

    @GetMapping("/requests/outgoing")
    public Result<?> outgoing(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(service.outgoing(userId));
    }

    @PostMapping("/requests/{id}/accept")
    public Result<?> accept(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long id) {
        return Result.ok(service.accept(userId, id));
    }

    @PostMapping("/requests/{id}/reject")
    public Result<?> reject(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long id) {
        service.reject(userId, id);
        return Result.ok();
    }

    @PostMapping("/requests/{id}/cancel")
    public Result<?> cancel(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long id) {
        service.cancel(userId, id);
        return Result.ok();
    }

    @GetMapping
    public Result<?> friends(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(service.friends(userId));
    }

    @GetMapping("/{targetId}/status")
    public Result<?> status(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long targetId) {
        return Result.ok(service.status(userId, targetId));
    }

    @DeleteMapping("/{targetId}")
    public Result<?> remove(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long targetId) {
        service.remove(userId, targetId);
        return Result.ok();
    }

    @PostMapping("/{targetId}/block")
    public Result<?> block(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                           @PathVariable Long targetId,
                           @Valid @RequestBody(required = false) BlockBody body) {
        service.block(userId, targetId, body == null ? null : body.reason());
        return Result.ok();
    }

    @DeleteMapping("/{targetId}/block")
    public Result<?> unblock(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                             @PathVariable Long targetId) {
        service.unblock(userId, targetId);
        return Result.ok();
    }

    @GetMapping("/blocked")
    public Result<?> blocked(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(service.blocked(userId));
    }
}
