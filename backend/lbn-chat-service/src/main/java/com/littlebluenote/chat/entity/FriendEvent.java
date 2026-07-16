package com.littlebluenote.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_friend_event")
public class FriendEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long relationId;
    private Long actorId;
    private Long peerId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
