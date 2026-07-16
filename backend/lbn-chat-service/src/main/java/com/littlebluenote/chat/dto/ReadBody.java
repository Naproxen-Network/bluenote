package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.Positive;

public record ReadBody(@Positive Long lastMessageId) {}
