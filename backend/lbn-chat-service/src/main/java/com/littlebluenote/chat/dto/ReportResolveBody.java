package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportResolveBody(@NotBlank @Size(max = 500) String resolution) {}
