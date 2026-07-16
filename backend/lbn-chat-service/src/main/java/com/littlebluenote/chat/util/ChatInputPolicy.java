package com.littlebluenote.chat.util;

import com.littlebluenote.chat.exception.BusinessException;

import java.util.regex.Pattern;

public final class ChatInputPolicy {
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private ChatInputPolicy() {}

    public static String requireClientMessageId(String value) {
        if (value == null || !CLIENT_ID.matcher(value).matches()) {
            throw BusinessException.badRequest("clientMessageId must be 8-64 safe characters");
        }
        return value;
    }

    public static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest("Message cannot be empty");
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw BusinessException.badRequest("Message exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
