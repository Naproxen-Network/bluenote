package com.littlebluenote.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.chat.entity.FriendRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FriendRelationMapper extends BaseMapper<FriendRelation> {
    @Insert("""
        INSERT INTO lbn_friend_relation
          (user_low_id, user_high_id, requester_id, request_message, status,
           requested_at, version, created_at, updated_at)
        VALUES (#{low}, #{high}, #{requester}, #{message}, 'PENDING',
                CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
        """)
    int ensurePair(@Param("low") long low, @Param("high") long high,
                   @Param("requester") long requester, @Param("message") String message);

    @Select("""
        SELECT * FROM lbn_friend_relation
        WHERE user_low_id = #{low} AND user_high_id = #{high}
        FOR UPDATE
        """)
    FriendRelation selectPairForUpdate(@Param("low") long low, @Param("high") long high);
}
