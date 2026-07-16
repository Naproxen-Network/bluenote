package com.littlebluenote.chat.websocket;

import com.littlebluenote.chat.dto.TypingBody;
import com.littlebluenote.chat.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class TypingController {
    private final ChatService chat;
    private final SimpMessagingTemplate messaging;

    public TypingController(ChatService chat, SimpMessagingTemplate messaging) {
        this.chat = chat;
        this.messaging = messaging;
    }

    @MessageMapping("/chat/typing")
    public void typing(TypingBody body, Principal principal) {
        if (principal == null || body == null || body.conversationId() == null || body.targetUserId() == null) return;
        long userId = Long.parseLong(principal.getName());
        long target = chat.requireTypingTarget(userId, body.conversationId(), body.targetUserId());
        messaging.convertAndSendToUser(String.valueOf(target), "/queue/typing",
                Map.of("conversationId", body.conversationId(), "userId", userId, "typing", body.typing()));
    }
}
