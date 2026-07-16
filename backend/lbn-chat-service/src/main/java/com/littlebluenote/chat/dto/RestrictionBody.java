package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RestrictionBody(
        @NotNull @Positive Long userId,
        @NotBlank String type,
        @NotBlank @Size(max = 500) String reason,
        LocalDateTime endsAt) {}
