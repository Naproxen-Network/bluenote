package com.littlebluenote.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String clientMessageId;
    private String messageType;
    private String content;
    private String status;
    private Long replyToId;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
    private LocalDateTime recalledAt;
}
