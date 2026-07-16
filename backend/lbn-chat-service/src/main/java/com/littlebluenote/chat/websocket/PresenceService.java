package com.littlebluenote.chat.websocket;

import com.littlebluenote.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {
    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<Long, Set<String>> localSessions = new ConcurrentHashMap<>();

    public PresenceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null || accessor.getSessionId() == null) return;
        long userId = Long.parseLong(principal.getName());
        localSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(accessor.getSessionId());
        refresh(userId);
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;
        long userId = Long.parseLong(principal.getName());
        Set<String> sessions = localSessions.get(userId);
        if (sessions != null) {
            sessions.remove(event.getSessionId());
            if (sessions.isEmpty()) {
                localSessions.remove(userId);
                // Keep the short-lived key: another service instance may still own a
                // session for this user and will continue refreshing the same presence.
            }
        }
    }

    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        localSessions.keySet().forEach(this::refresh);
    }

    public boolean isOnline(long userId) {
        try { return Boolean.TRUE.equals(redis.hasKey(Constants.REDIS_CHAT_PRESENCE_PREFIX + userId)); }
        catch (Exception e) { return localSessions.containsKey(userId); }
    }

    private void refresh(long userId) {
        try { redis.opsForValue().set(Constants.REDIS_CHAT_PRESENCE_PREFIX + userId, "1", TTL); }
        catch (Exception e) { log.debug("Presence refresh skipped: {}", e.getMessage()); }
    }
}
