package com.littlebluenote.chat.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.littlebluenote.chat.entity.ChatOutbox;
import com.littlebluenote.chat.entity.ChatRestriction;
import com.littlebluenote.chat.mapper.ChatOutboxMapper;
import com.littlebluenote.chat.mapper.ChatRestrictionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ChatMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(ChatMaintenanceJob.class);

    private final ChatOutboxMapper outboxMapper;
    private final ChatRestrictionMapper restrictionMapper;
    private final int publishedRetentionDays;

    public ChatMaintenanceJob(ChatOutboxMapper outboxMapper, ChatRestrictionMapper restrictionMapper,
                              @Value("${chat.outbox.published-retention-days:7}") int publishedRetentionDays) {
        this.outboxMapper = outboxMapper;
        this.restrictionMapper = restrictionMapper;
        this.publishedRetentionDays = Math.max(1, publishedRetentionDays);
    }

    @Scheduled(cron = "0 17 * * * *")
    public void maintain() {
        LocalDateTime now = LocalDateTime.now();
        int expired = restrictionMapper.update(null, new UpdateWrapper<ChatRestriction>()
                .set("active", false)
                .eq("active", true)
                .isNotNull("ends_at")
                .le("ends_at", now));
        int removed = outboxMapper.delete(new UpdateWrapper<ChatOutbox>()
                .eq("status", "PUBLISHED")
                .le("published_at", now.minusDays(publishedRetentionDays)));
        if (expired > 0 || removed > 0) {
            log.info("Chat maintenance expiredRestrictions={} removedOutboxEvents={}", expired, removed);
        }
    }
}
