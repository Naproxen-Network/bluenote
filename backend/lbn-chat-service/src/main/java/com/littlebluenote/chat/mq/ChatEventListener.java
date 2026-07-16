package com.littlebluenote.chat.mq;

import com.littlebluenote.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class ChatEventListener {
    private static final Logger log = LoggerFactory.getLogger(ChatEventListener.class);

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final String instanceId;

    public ChatEventListener(SimpMessagingTemplate messaging, StringRedisTemplate redis,
                             @Qualifier("chatInstanceId") String instanceId) {
        this.messaging = messaging;
        this.redis = redis;
        this.instanceId = instanceId;
    }

    @RabbitListener(queues = "#{chatWebsocketQueue.name}")
    public void deliver(Map<String, Object> event) {
        String eventId = String.valueOf(event.get("eventId"));
        if (!firstDelivery(eventId)) return;
        Object recipient = event.get("recipientId");
        Object destination = event.get("destination");
        if (recipient == null || destination == null) {
            log.warn("Discarding malformed chat event {}", eventId);
            return;
        }
        messaging.convertAndSendToUser(String.valueOf(((Number) recipient).longValue()),
                "/queue/" + destination, event.get("data"));
    }

    private boolean firstDelivery(String eventId) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(
                    Constants.REDIS_CHAT_EVENT_PREFIX + instanceId + ":" + eventId, "1", Duration.ofHours(24));
            return !Boolean.FALSE.equals(first);
        } catch (Exception e) {
            return true;
        }
    }
}
