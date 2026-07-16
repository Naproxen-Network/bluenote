package com.littlebluenote.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.chat.entity.ChatOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatOutboxMapper extends BaseMapper<ChatOutbox> {
    @Update("""
        UPDATE lbn_chat_outbox
        SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP(3)
        WHERE id = #{id} AND status IN ('PENDING', 'FAILED')
        """)
    int claim(@Param("id") long id);

    @Update("""
        UPDATE lbn_chat_outbox
        SET status = 'FAILED', next_retry_at = CURRENT_TIMESTAMP(3),
            last_error = 'Recovered stale processing event', updated_at = CURRENT_TIMESTAMP(3)
        WHERE status = 'PROCESSING'
          AND updated_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE)
        """)
    int recoverStale();
}
