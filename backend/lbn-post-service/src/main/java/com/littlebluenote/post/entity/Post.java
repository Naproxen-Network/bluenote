package com.littlebluenote.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String field;
    private String content;
    private String tags;
    private String image;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
}
