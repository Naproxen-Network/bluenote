package com.littlebluenote.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_conversation_member")
public class ConversationMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long userId;
    private Long lastReadMessageId;
    private Long lastDeliveredMessageId;
    private Integer unreadCount;
    private Boolean muted;
    private Boolean pinned;
    private Long deletedBeforeId;
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
}
