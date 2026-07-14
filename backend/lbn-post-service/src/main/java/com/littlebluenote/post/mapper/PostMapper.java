package com.littlebluenote.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.post.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
    @Update("UPDATE lbn_post SET like_count = like_count + #{delta} WHERE id = #{id}")
    void addLike(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE lbn_post SET favorite_count = favorite_count + #{delta} WHERE id = #{id}")
    void addFavorite(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE lbn_post SET comment_count = comment_count + #{delta} WHERE id = #{id}")
    void addComment(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE lbn_post SET view_count = view_count + 1 WHERE id = #{id}")
    void addView(@Param("id") Long id);
}
