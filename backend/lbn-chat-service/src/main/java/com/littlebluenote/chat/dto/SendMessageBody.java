package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageBody(
        @NotBlank @Size(min = 8, max = 64) String clientMessageId,
        @NotBlank @Size(max = 2000) String content,
        Long replyToId) {}
