package com.littlebluenote.common;

/** Shared constants across services (RabbitMQ topology, headers, JWT). */
public final class Constants {
    private Constants() {}

    // ---- Auth ----
    public static final String JWT_SECRET =
            "little-blue-note-super-secret-key-for-jwt-signing-0123456789";
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

    // ---- Redis keys ----
    public static final String REDIS_REC_PREFIX = "lbn:rec:";          // recommendation cache per user
    public static final String REDIS_CROSS_BOOST = "lbn:crossboost";   // dynamic cross-layer boost hash
    public static final String REDIS_HOT_POSTS = "lbn:hotposts";       // trending posts zset
    public static final String REDIS_ONLINE = "lbn:online";            // online users set
}
