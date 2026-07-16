package com.littlebluenote.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.littlebluenote.chat.entity.ChatRestriction;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.chat.mapper.ChatRestrictionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RestrictionService {
    private final ChatRestrictionMapper mapper;

    public RestrictionService(ChatRestrictionMapper mapper) {
        this.mapper = mapper;
    }

    public void requireAllowed(long userId, String type) {
        LocalDateTime now = LocalDateTime.now();
        long count = mapper.selectCount(new QueryWrapper<ChatRestriction>()
                .eq("user_id", userId).eq("restriction_type", type).eq("active", true)
                .le("starts_at", now)
                .and(q -> q.isNull("ends_at").or().gt("ends_at", now)));
        if (count > 0) throw BusinessException.forbidden("This account is currently restricted from this action");
    }
}
