package com.littlebluenote.chat.controller;

import com.littlebluenote.chat.dto.ReadBody;
import com.littlebluenote.chat.dto.ReportBody;
import com.littlebluenote.chat.dto.SendMessageBody;
import com.littlebluenote.chat.service.ChatService;
import com.littlebluenote.chat.websocket.PresenceService;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService service;
    private final PresenceService presence;

    public ChatController(ChatService service, PresenceService presence) {
        this.service = service;
        this.presence = presence;
    }

    @PostMapping("/conversations/private/{friendId}")
    public Result<?> open(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                          @PathVariable Long friendId) {
        return Result.ok(service.open(userId, friendId));
    }

    @GetMapping("/conversations")
    public Result<?> conversations(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(service.conversations(userId));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<?> history(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                             @PathVariable Long id,
                             @RequestParam(required = false) Long beforeId,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(service.history(userId, id, beforeId, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    public Result<?> send(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                          @PathVariable Long id,
                          @Valid @RequestBody SendMessageBody body) {
        return Result.ok(service.send(userId, id, body.clientMessageId(), body.content(), body.replyToId()));
    }

    @PutMapping("/conversations/{id}/read")
    public Result<?> read(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                          @PathVariable Long id,
                          @Valid @RequestBody(required = false) ReadBody body) {
        return Result.ok(service.markRead(userId, id, body == null ? null : body.lastMessageId()));
    }

    @PostMapping("/messages/{id}/recall")
    public Result<?> recall(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long id) {
        return Result.ok(service.recall(userId, id));
    }

    @PostMapping("/messages/{id}/report")
    public Result<?> report(@RequestHeader(Constants.HEADER_USER_ID) Long userId,
                            @PathVariable Long id,
                            @Valid @RequestBody ReportBody body) {
        return Result.ok(service.report(userId, id, body.type(), body.description()));
    }

    @GetMapping("/unread-count")
    public Result<?> unread(@RequestHeader(Constants.HEADER_USER_ID) Long userId) {
        return Result.ok(Map.of("count", service.unreadTotal(userId)));
    }

    @GetMapping("/presence")
    public Result<?> presence(@RequestParam String ids) {
        Map<Long, Boolean> out = new LinkedHashMap<>();
        Arrays.stream(ids.split(",")).filter(s -> !s.isBlank()).limit(100).forEach(raw -> {
            try {
                long id = Long.parseLong(raw);
                if (id > 0) out.put(id, presence.isOnline(id));
            } catch (NumberFormatException ignored) { }
        });
        return Result.ok(out);
    }
}
