package com.littlebluenote.chat.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.littlebluenote.chat.entity.ChatOutbox;
import com.littlebluenote.chat.mapper.ChatOutboxMapper;
import com.littlebluenote.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final ChatOutboxMapper mapper;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxPublisher(ChatOutboxMapper mapper, RabbitTemplate rabbit, ObjectMapper objectMapper,
                           @Value("${chat.outbox.batch-size:100}") int batchSize) {
        this.mapper = mapper;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        List<ChatOutbox> rows = mapper.selectList(new QueryWrapper<ChatOutbox>()
                .in("status", "PENDING", "FAILED").le("next_retry_at", now)
                .lt("retry_count", 12).orderByAsc("id").last("LIMIT " + Math.max(1, batchSize)));
        for (ChatOutbox row : rows) {
            if (mapper.claim(row.getId()) != 1) continue;
            publish(row);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void recoverStale() {
        int recovered = mapper.recoverStale();
        if (recovered > 0) log.warn("Recovered {} stale chat outbox events", recovered);
    }

    private void publish(ChatOutbox row) {
        try {
            Map<String, Object> payload = objectMapper.readValue(row.getPayload(), new TypeReference<>() {});
            CorrelationData correlation = new CorrelationData(row.getEventId());
            rabbit.convertAndSend(Constants.EXCHANGE_CHAT, Constants.RK_CHAT_EVENT, payload, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("Broker rejected event: " + confirm.getReason());

            row.setStatus("PUBLISHED");
            row.setPublishedAt(LocalDateTime.now());
            row.setLastError(null);
            mapper.updateById(row);
        } catch (Exception e) {
            int retries = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
            long delay = Math.min(300, 1L << Math.min(retries, 8));
            row.setStatus("FAILED");
            row.setRetryCount(retries);
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            row.setLastError(message.length() <= 500 ? message : message.substring(0, 500));
            mapper.updateById(row);
            log.debug("Chat outbox publish retry event={} attempt={}: {}", row.getEventId(), retries, message);
        }
    }
}
