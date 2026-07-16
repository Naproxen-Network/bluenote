package com.littlebluenote.chat.websocket;

import java.security.Principal;

public record UserPrincipal(long userId, String role) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
