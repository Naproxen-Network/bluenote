package com.littlebluenote.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.littlebluenote.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Insert("""
        INSERT IGNORE INTO lbn_message
          (conversation_id, sender_id, client_message_id, message_type, content,
           status, reply_to_id, created_at, edited_at, recalled_at)
        VALUES
          (#{conversationId}, #{senderId}, #{clientMessageId}, #{messageType}, #{content},
           #{status}, #{replyToId}, #{createdAt}, #{editedAt}, #{recalledAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIdempotent(ChatMessage message);
}
