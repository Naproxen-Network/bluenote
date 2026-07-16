package com.littlebluenote.user.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates stable, public SVG avatars for users who register through the application.
 * Usernames are ASCII-only, so the rendered text cannot inject SVG markup.
 */
@Service
public class InitialAvatarService {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    public String publicUrl(String username) {
        return "/api/user/avatar/" + normalizeAndValidate(username) + ".svg";
    }

    public String initials(String username) {
        String normalized = normalizeAndValidate(username);
        return normalized.substring(0, Math.min(2, normalized.length())).toUpperCase(Locale.ROOT);
    }

    public String renderSvg(String username) {
        String initials = initials(username);
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200" role="img" aria-label="%s avatar">
                  <defs>
                    <linearGradient id="avatar-gradient" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="#2f6ea3"/>
                      <stop offset="1" stop-color="#8fb8dd"/>
                    </linearGradient>
                  </defs>
                  <rect width="200" height="200" rx="32" fill="url(#avatar-gradient)"/>
                  <text x="100" y="110" text-anchor="middle" dominant-baseline="middle" font-family="Georgia, serif" font-size="80" font-weight="700" fill="#ffffff">%s</text>
                </svg>
                """.formatted(initials, initials);
    }

    private String normalizeAndValidate(String username) {
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!VALID_USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-32 ASCII letters, numbers or underscores; Chinese characters are not allowed");
        }
        return normalized;
    }
}
