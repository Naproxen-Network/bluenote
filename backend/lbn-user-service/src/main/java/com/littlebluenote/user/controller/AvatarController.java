package com.littlebluenote.user.controller;

import com.littlebluenote.user.service.InitialAvatarService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** Public endpoint used by browser image requests, which do not carry the JWT header. */
@RestController
@RequestMapping("/api/user/avatar")
public class AvatarController {

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml;charset=UTF-8");

    private final InitialAvatarService avatarService;

    public AvatarController(InitialAvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping(value = "/{username}.svg", produces = "image/svg+xml;charset=UTF-8")
    public ResponseEntity<String> avatar(@PathVariable String username) {
        return ResponseEntity.ok()
                .contentType(SVG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatarService.renderSvg(username));
    }
}
