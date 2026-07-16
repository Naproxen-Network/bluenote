package com.littlebluenote.post.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.littlebluenote.common.Constants;
import com.littlebluenote.post.entity.*;
import com.littlebluenote.post.mapper.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PostService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final RabbitTemplate rabbit;
    private final StringRedisTemplate redis;

    public PostService(PostMapper postMapper, CommentMapper commentMapper, LikeMapper likeMapper,
                       FavoriteMapper favoriteMapper, RabbitTemplate rabbit, StringRedisTemplate redis) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.rabbit = rabbit;
        this.redis = redis;
    }

    public IPage<Post> page(int page, int size, String field, Long authorId) {
        QueryWrapper<Post> qw = new QueryWrapper<>();
        if (field != null && !field.isBlank()) qw.eq("field", field);
        if (authorId != null) qw.eq("author_id", authorId);
        qw.orderByDesc("id");
        return postMapper.selectPage(new Page<>(page, size), qw);
    }

    public List<Post> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Post> list = postMapper.selectBatchIds(ids);
        // preserve requested order (matters for recommendation ranking)
        Map<Long, Post> m = new HashMap<>();
        for (Post p : list) m.put(p.getId(), p);
        List<Post> ordered = new ArrayList<>();
        for (Long id : ids) if (m.containsKey(id)) ordered.add(m.get(id));
        return ordered;
    }

    public List<Post> all() {
        return postMapper.selectList(new QueryWrapper<Post>().orderByDesc("id"));
    }

    public Post get(Long id) {
        Post p = postMapper.selectById(id);
        if (p != null) {
            postMapper.addView(id);
            redis.opsForZSet().incrementScore(Constants.REDIS_HOT_POSTS, String.valueOf(id), 0.2);
        }
        return p;
    }

    @Transactional
    public Post publish(Long authorId, String field, String content, String tags, String image) {
        Post p = new Post();
        p.setAuthorId(authorId);
        p.setField(field);
        p.setContent(content);
        p.setTags(tags);
        p.setImage(image == null ? "topic:" + field : image);
        p.setLikeCount(0);
        p.setCommentCount(0);
        p.setFavoriteCount(0);
        p.setViewCount(0);
        postMapper.insert(p);
        // notify recommend-service so the new post enters the candidate pool immediately
        publishEvent(Constants.RK_POST_CREATED, Map.of(
                "postId", p.getId(), "authorId", authorId, "field", field));
        return p;
    }

    @Transactional
    public boolean like(Long postId, Long userId) {
        boolean exists = likeMapper.selectCount(new QueryWrapper<Like>()
                .eq("post_id", postId).eq("user_id", userId)) > 0;
        if (exists) {
            likeMapper.delete(new QueryWrapper<Like>().eq("post_id", postId).eq("user_id", userId));
            postMapper.addLike(postId, -1);
            bumpHot(postId, -1.0);
            return false;
        }
        Like l = new Like();
        l.setPostId(postId);
        l.setUserId(userId);
        likeMapper.insert(l);
        postMapper.addLike(postId, 1);
        bumpHot(postId, 1.0);
        publishEvent(Constants.RK_POST_INTERACT, Map.of(
                "type", "like", "postId", postId, "userId", userId));
        return true;
    }

    @Transactional
    public boolean favorite(Long postId, Long userId) {
        boolean exists = favoriteMapper.selectCount(new QueryWrapper<Favorite>()
                .eq("post_id", postId).eq("user_id", userId)) > 0;
        if (exists) {
            favoriteMapper.delete(new QueryWrapper<Favorite>().eq("post_id", postId).eq("user_id", userId));
            postMapper.addFavorite(postId, -1);
            bumpHot(postId, -1.5);
            return false;
        }
        Favorite f = new Favorite();
        f.setPostId(postId);
        f.setUserId(userId);
        favoriteMapper.insert(f);
        postMapper.addFavorite(postId, 1);
        bumpHot(postId, 1.5);
        publishEvent(Constants.RK_POST_INTERACT, Map.of(
                "type", "favorite", "postId", postId, "userId", userId));
        return true;
    }

    @Transactional
    public Comment comment(Long postId, Long userId, String content) {
        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        commentMapper.insert(c);
        postMapper.addComment(postId, 1);
        bumpHot(postId, 0.8);
        return c;
    }

    public List<Comment> comments(Long postId) {
        return commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("post_id", postId).orderByDesc("id"));
    }

    public boolean isLiked(Long postId, Long userId) {
        return likeMapper.selectCount(new QueryWrapper<Like>()
                .eq("post_id", postId).eq("user_id", userId)) > 0;
    }

    public boolean isFavorited(Long postId, Long userId) {
        return favoriteMapper.selectCount(new QueryWrapper<Favorite>()
                .eq("post_id", postId).eq("user_id", userId)) > 0;
    }

    public List<Long> favoritePostIds(Long userId) {
        return favoriteMapper.selectList(new QueryWrapper<Favorite>().eq("user_id", userId))
                .stream().map(Favorite::getPostId).toList();
    }

    /** trending post ids from Redis zset (used by recommend-service as a fallback signal) */
    public Set<String> hotPostIds(int n) {
        Set<String> s = redis.opsForZSet().reverseRange(Constants.REDIS_HOT_POSTS, 0, n - 1);
        return s == null ? Set.of() : s;
    }

    private void bumpHot(Long postId, double delta) {
        redis.opsForZSet().incrementScore(Constants.REDIS_HOT_POSTS, String.valueOf(postId), delta);
    }

    private void publishEvent(String routingKey, Map<String, Object> payload) {
        try {
            rabbit.convertAndSend(Constants.EXCHANGE_POST, routingKey, payload);
        } catch (Exception ignore) {
            // MQ outage must not break the user-facing write path
        }
    }
}
