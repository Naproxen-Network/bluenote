package com.littlebluenote.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_chat_report")
public class ChatReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private Long reportedUserId;
    private Long messageId;
    private String reportType;
    private String description;
    private String status;
    private Long handledBy;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
