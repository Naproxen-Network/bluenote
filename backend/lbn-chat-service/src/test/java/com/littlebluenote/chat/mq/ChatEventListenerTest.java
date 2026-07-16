package com.littlebluenote.chat.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatEventListenerTest {
    private SimpMessagingTemplate messaging;
    private ValueOperations<String, String> values;
    private ChatEventListener listener;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        listener = new ChatEventListener(messaging, redis, "test-instance");
    }

    @Test
    void deliversFirstEventToTheAuthenticatedUsersPrivateQueue() {
        Map<String, Object> data = Map.of("kind", "MESSAGE", "conversationId", 9L);
        Map<String, Object> event = Map.of(
                "eventId", "event-1",
                "recipientId", 42L,
                "destination", "messages",
                "data", data);
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(true);

        listener.deliver(event);

        verify(messaging).convertAndSendToUser("42", "/queue/messages", data);
    }

    @Test
    void suppressesRedeliveryOfTheSameOutboxEvent() {
        Map<String, Object> event = Map.of(
                "eventId", "event-2",
                "recipientId", 42L,
                "destination", "friend-events",
                "data", Map.of("kind", "FRIEND_ACCEPTED"));
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        listener.deliver(event);

        verifyNoInteractions(messaging);
    }
}
