package com.littlebluenote.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.chat.entity.UserBlock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlock> {
    @Insert("""
        INSERT INTO lbn_user_block (blocker_id, blocked_id, reason, created_at)
        VALUES (#{blocker}, #{blocked}, #{reason}, CURRENT_TIMESTAMP(3))
        ON DUPLICATE KEY UPDATE reason = VALUES(reason)
        """)
    int upsert(@Param("blocker") long blocker, @Param("blocked") long blocked,
               @Param("reason") String reason);
}
