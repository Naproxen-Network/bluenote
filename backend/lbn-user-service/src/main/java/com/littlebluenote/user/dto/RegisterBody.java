package com.littlebluenote.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterBody(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_]+$",
                message = "Username may contain only ASCII letters, numbers and underscores; Chinese characters are not allowed")
        String username,

        @NotBlank
        @Size(min = 2, max = 64)
        String displayName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password) {
}
