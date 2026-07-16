package com.littlebluenote.recommend.mq;

import com.littlebluenote.common.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Committee-driven influence should fade if the layer stops changing. Every 30s we
 * exponentially decay the dynamic boosts so the network "cools down" toward its
 * structural baseline, keeping the recommendation dynamic rather than sticky.
 */
@Component
public class BoostDecayTask {

    private final StringRedisTemplate redis;

    public BoostDecayTask(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Scheduled(fixedDelay = 30_000)
    public void decay() {
        Map<Object, Object> boosts = redis.opsForHash().entries(Constants.REDIS_CROSS_BOOST);
        if (boosts.isEmpty()) return;
        boolean changed = false;
        for (var e : boosts.entrySet()) {
            double v;
            try { v = Double.parseDouble(String.valueOf(e.getValue())); } catch (Exception ex) { continue; }
            double nv = v * 0.85;
            if (Math.abs(nv) < 0.01) {
                redis.opsForHash().delete(Constants.REDIS_CROSS_BOOST, e.getKey());
            } else {
                redis.opsForHash().put(Constants.REDIS_CROSS_BOOST, e.getKey(), String.valueOf(nv));
            }
            changed = true;
        }
        if (changed) redis.opsForValue().increment("lbn:rec:version");
    }
}
