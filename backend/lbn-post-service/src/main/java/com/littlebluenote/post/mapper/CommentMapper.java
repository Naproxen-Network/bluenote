package com.littlebluenote.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.post.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
