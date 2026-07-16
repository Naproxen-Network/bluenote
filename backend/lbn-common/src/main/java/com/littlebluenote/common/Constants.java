package com.littlebluenote.common;

import java.nio.charset.StandardCharsets;

/** Shared constants across services (RabbitMQ topology, headers, JWT). */
public final class Constants {
    private Constants() {}

    // ---- Auth ----
    public static final String JWT_SECRET = loadJwtSecret();
    public static final long JWT_TTL_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    // ---- RabbitMQ: post/interaction events ----
    public static final String EXCHANGE_POST = "lbn.post.exchange";
    public static final String QUEUE_POST_EVENT = "lbn.post.event.queue";
    public static final String RK_POST_CREATED = "post.created";
    public static final String RK_POST_INTERACT = "post.interact";

    // ---- RabbitMQ: cross-layer (committee) events from the Node layer-sync service ----
    public static final String EXCHANGE_LAYER = "lbn.layer.exchange";
    public static final String QUEUE_LAYER_EVENT = "lbn.layer.event.queue";
    public static final String RK_LAYER_CHANGED = "committee.changed";

    // ---- RabbitMQ: durable friend/chat events ----
    public static final String EXCHANGE_CHAT = "lbn.chat.exchange";
    public static final String QUEUE_CHAT_WEBSOCKET = "lbn.chat.websocket.queue";
    public static final String QUEUE_CHAT_DEAD = "lbn.chat.dead.queue";
    public static final String EXCHANGE_CHAT_DEAD = "lbn.chat.dead.exchange";
    public static final String RK_CHAT_EVENT = "chat.event";

    // ---- Redis keys ----
    public static final String REDIS_REC_PREFIX = "lbn:rec:";          // recommendation cache per user
    public static final String REDIS_CROSS_BOOST = "lbn:crossboost";   // dynamic cross-layer boost hash
    public static final String REDIS_HOT_POSTS = "lbn:hotposts";       // trending posts zset
    public static final String REDIS_ONLINE = "lbn:online";            // online users set
    public static final String REDIS_CHAT_EVENT_PREFIX = "lbn:chat:event:";
    public static final String REDIS_CHAT_PRESENCE_PREFIX = "lbn:chat:presence:";

    private static String loadJwtSecret() {
        String secret = System.getenv("LBN_JWT_SECRET");
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("LBN_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return secret;
    }
}
