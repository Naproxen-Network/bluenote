package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FriendRequestBody(
        @Positive Long targetUserId,
        @Size(min = 3, max = 32) String targetUsername,
        @Size(max = 200) String message) {}
