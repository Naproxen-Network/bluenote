package com.littlebluenote.chat.service;

import com.littlebluenote.chat.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {
    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkMessage(long userId) {
        check("lbn:chat:rate:message:" + userId + ":" + Instant.now().getEpochSecond() / 60,
                30, Duration.ofMinutes(2));
    }

    public void checkFriendRequest(long userId) {
        check("lbn:chat:rate:friend:" + userId + ":" + Instant.now().getEpochSecond() / 3600,
                50, Duration.ofHours(2));
    }

    private void check(String key, long limit, Duration ttl) {
        try {
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1) redis.expire(key, ttl);
            if (value != null && value > limit) throw BusinessException.forbidden("Too many requests; please try again later");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            // Redis failure must not make durable chat unavailable.
        }
    }
}
