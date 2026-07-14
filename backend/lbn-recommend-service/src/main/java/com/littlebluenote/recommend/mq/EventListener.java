package com.littlebluenote.recommend.mq;

import com.littlebluenote.common.Constants;
import com.littlebluenote.recommend.engine.MlhrModel;
import com.littlebluenote.recommend.engine.RecommendEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes cross-layer (committee) change events and post events, and mutates the
 * dynamic state that the recommender reads at runtime. This is what makes the 发现
 * feed and search results react in real time to shifts in the other network layer.
 */
@Component
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final StringRedisTemplate redis;
    private final MlhrModel model;
    private final RecommendEngine engine;

    public EventListener(StringRedisTemplate redis, MlhrModel model, RecommendEngine engine) {
        this.redis = redis;
        this.model = model;
        this.engine = engine;
    }

    @RabbitListener(queues = Constants.QUEUE_LAYER_EVENT)
    public void onLayerChanged(Map<String, Object> event) {
        Object uidObj = event.get("billsUserId");
        if (uidObj == null) return;
        String uid = String.valueOf(((Number) toNumber(uidObj)).intValue());
        double delta = toNumber(event.getOrDefault("weightDelta", 0.5)).doubleValue();
        String action = String.valueOf(event.getOrDefault("action", "JOIN"));
        // CHAIR carries the most influence, LEAVE dampens
        double factor = switch (action) {
            case "CHAIR" -> 1.5;
            case "LEAVE" -> -0.6;
            default -> 1.0;
        };
        double signed = delta * factor * model.modulationSc;
        redis.opsForHash().increment(Constants.REDIS_CROSS_BOOST, uid, signed);
        redis.opsForValue().increment("lbn:rec:version");
        engine.invalidateSnapshot();
        log.info("[layer-sync] committee change user={} action={} boost+={}", uid, action, signed);
    }

    @RabbitListener(queues = Constants.QUEUE_POST_EVENT)
    public void onPostEvent(Map<String, Object> event) {
        redis.opsForValue().increment("lbn:rec:version");
        engine.invalidateSnapshot();
        log.debug("[post-event] {}", event);
    }

    private Number toNumber(Object o) {
        if (o instanceof Number n) return n;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
