package com.littlebluenote.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_user_chat_restriction")
public class ChatRestriction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String restrictionType;
    private String reason;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long operatorAdminId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
