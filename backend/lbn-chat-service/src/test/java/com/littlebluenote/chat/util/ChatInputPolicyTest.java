package com.littlebluenote.chat.util;

import com.littlebluenote.chat.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatInputPolicyTest {

    @Test
    void acceptsUuidAndTrimsText() {
        String id = "d2d967c2-59c1-4a5b-b2ea-e159f036cc4f";
        assertEquals(id, ChatInputPolicy.requireClientMessageId(id));
        assertEquals("hello", ChatInputPolicy.requireText("  hello  ", 20));
    }

    @Test
    void rejectsMalformedClientMessageIds() {
        assertThrows(BusinessException.class, () -> ChatInputPolicy.requireClientMessageId("short"));
        assertThrows(BusinessException.class, () -> ChatInputPolicy.requireClientMessageId("invalid id with spaces"));
        assertThrows(BusinessException.class, () -> ChatInputPolicy.requireClientMessageId("x".repeat(65)));
    }

    @Test
    void rejectsBlankAndOversizedMessages() {
        assertThrows(BusinessException.class, () -> ChatInputPolicy.requireText("   ", 20));
        assertThrows(BusinessException.class, () -> ChatInputPolicy.requireText("123456", 5));
        assertEquals("你好", ChatInputPolicy.requireText("你好", 2));
    }
}
