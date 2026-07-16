package com.littlebluenote.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lbn_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String displayName;
    private String party;
    private String leaning;
    private String gender;
    private String position;
    private String almaMater;
    private String educationLevel;
    private String state;
    private String bio;
    private String interests;   // pipe-separated
    private Long committeeId;
    private String avatar;
    private LocalDateTime createdAt;
}
