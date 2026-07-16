package com.littlebluenote.post.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.littlebluenote.common.Constants;
import com.littlebluenote.common.Result;
import com.littlebluenote.post.entity.Comment;
import com.littlebluenote.post.entity.Post;
import com.littlebluenote.post.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/post")
public class PostController {

    private final PostService svc;

    public PostController(PostService svc) {
        this.svc = svc;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String field,
                                            @RequestParam(required = false) Long authorId) {
        IPage<Post> p = svc.page(page, size, field, authorId);
        Map<String, Object> m = new HashMap<>();
        m.put("total", p.getTotal());
        m.put("records", p.getRecords());
        return Result.ok(m);
    }

    /** internal: fetch posts by id list in the given order (used by recommend-service via Feign) */
    @GetMapping("/batch")
    public Result<List<Post>> batch(@RequestParam("ids") String ids) {
        List<Long> list = Arrays.stream(ids.split(","))
                .filter(s -> !s.isBlank()).map(Long::valueOf).collect(Collectors.toList());
        return Result.ok(svc.byIds(list));
    }

    /** internal: full candidate pool for the recommender */
    @GetMapping("/all")
    public Result<List<Post>> all() {
        return Result.ok(svc.all());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id,
                                              @RequestHeader(value = Constants.HEADER_USER_ID, required = false) Long uid) {
        Post p = svc.get(id);
        if (p == null) return Result.error("Post not found");
        Map<String, Object> m = new HashMap<>();
        m.put("post", p);
        m.put("comments", svc.comments(id));
        if (uid != null) {
            m.put("liked", svc.isLiked(id, uid));
            m.put("favorited", svc.isFavorited(id, uid));
        }
        return Result.ok(m);
    }

    @PostMapping("/publish")
    public Result<Post> publish(@RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                @RequestBody Map<String, String> body) {
        Post p = svc.publish(uid, body.getOrDefault("field", "Governance & Reform"),
                body.get("content"), body.getOrDefault("tags", ""), body.get("image"));
        return Result.ok(p);
    }

    @PostMapping("/{id}/like")
    public Result<Boolean> like(@PathVariable Long id,
                                @RequestHeader(Constants.HEADER_USER_ID) Long uid) {
        return Result.ok(svc.like(id, uid));
    }

    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id,
                                    @RequestHeader(Constants.HEADER_USER_ID) Long uid) {
        return Result.ok(svc.favorite(id, uid));
    }

    @PostMapping("/{id}/comment")
    public Result<Comment> comment(@PathVariable Long id,
                                   @RequestHeader(Constants.HEADER_USER_ID) Long uid,
                                   @RequestBody Map<String, String> body) {
        return Result.ok(svc.comment(id, uid, body.get("content")));
    }

    @GetMapping("/favorites")
    public Result<List<Long>> favorites(@RequestHeader(Constants.HEADER_USER_ID) Long uid) {
        return Result.ok(svc.favoritePostIds(uid));
    }

    @GetMapping("/hot")
    public Result<Set<String>> hot(@RequestParam(defaultValue = "20") int n) {
        return Result.ok(svc.hotPostIds(n));
    }
}
