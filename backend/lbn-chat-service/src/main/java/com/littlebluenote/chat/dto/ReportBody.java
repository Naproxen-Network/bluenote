package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportBody(
        @NotBlank @Size(max = 32) String type,
        @Size(max = 500) String description) {}
