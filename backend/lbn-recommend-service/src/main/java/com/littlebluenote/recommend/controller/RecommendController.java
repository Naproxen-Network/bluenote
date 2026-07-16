package com.littlebluenote.recommend.controller;

import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import com.littlebluenote.recommend.engine.RecommendEngine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecommendController {

    private final RecommendEngine engine;
    private final StringRedisTemplate redis;

    public RecommendController(RecommendEngine engine, StringRedisTemplate redis) {
        this.engine = engine;
        this.redis = redis;
    }

    /** Discover feed (module 3) - personalised by the multi-layer hypergraph recommender. */
    @GetMapping("/recommend/feed")
    public Result<Map<String, Object>> feed(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "12") int size) {
        return Result.ok(engine.feed(uid.intValue(), page, size));
    }

    /** Search (module 6) - also driven by the algorithm (cross-layer re-ranking). */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                              @RequestParam String q,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        return Result.ok(engine.search(uid.intValue(), q, page, size));
    }

    @GetMapping("/recommend/people")
    public Result<List<Map<String, Object>>> people(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                                     @RequestParam(defaultValue = "8") int n) {
        return Result.ok(engine.suggestedUsers(uid.intValue(), n));
    }

    /** Inspect the live cross-layer boost state (used by the admin console / demo). */
    @GetMapping("/recommend/dynamic-state")
    public Result<Map<Object, Object>> dynamicState() {
        return Result.ok(redis.opsForHash().entries(Constants.REDIS_CROSS_BOOST));
    }
}
