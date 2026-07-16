package com.littlebluenote.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialAvatarServiceTest {

    private final InitialAvatarService service = new InitialAvatarService();

    @Test
    void createsStableUrlAndUppercaseInitials() {
        assertEquals("/api/user/avatar/alice_01.svg", service.publicUrl(" Alice_01 "));
        assertEquals("AL", service.initials("alice_01"));
    }

    @Test
    void rendersSvgInTheSameBlueInitialsStyleAsSeededUsers() {
        String svg = service.renderSvg("bruce");

        assertTrue(svg.contains(">BR</text>"));
        assertTrue(svg.contains("#2f6ea3"));
        assertTrue(svg.contains("#8fb8dd"));
    }

    @Test
    void rejectsChineseAndOtherUnsafeUsernameCharacters() {
        assertThrows(IllegalArgumentException.class, () -> service.publicUrl("测试user"));
        assertThrows(IllegalArgumentException.class, () -> service.renderSvg("ab<script>"));
    }
}
