package com.littlebluenote.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.chat.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {
    @Select("""
        SELECT * FROM lbn_conversation_member
        WHERE conversation_id = #{conversationId} AND user_id = #{userId}
        FOR UPDATE
        """)
    ConversationMember selectForUpdate(@Param("conversationId") long conversationId,
                                       @Param("userId") long userId);

    @Update("""
        UPDATE lbn_conversation_member
        SET unread_count = LEAST(unread_count + 1, 2147483647), updated_at = CURRENT_TIMESTAMP(3)
        WHERE conversation_id = #{conversationId} AND user_id = #{userId}
        """)
    int incrementUnread(@Param("conversationId") long conversationId,
                        @Param("userId") long userId);
}
