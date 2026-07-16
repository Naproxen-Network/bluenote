package com.littlebluenote.chat.dto;

public record TypingBody(Long conversationId, Long targetUserId, boolean typing) {}
