package com.littlebluenote.chat.dto;

import jakarta.validation.constraints.Size;

public record BlockBody(@Size(max = 255) String reason) {}
