package com.littlebluenote.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.littlebluenote.chat.entity.ChatOutbox;
import com.littlebluenote.chat.mapper.ChatOutboxMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {
    private final ChatOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public OutboxService(ChatOutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void enqueue(long recipientId, String destination, String eventType,
                        Long aggregateId, Object data) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("recipientId", recipientId);
        envelope.put("destination", destination);
        envelope.put("data", data);

        ChatOutbox row = new ChatOutbox();
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setAggregateId(aggregateId);
        try {
            row.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize chat event", e);
        }
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setNextRetryAt(LocalDateTime.now());
        mapper.insert(row);
    }
}
