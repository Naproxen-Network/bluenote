package com.littlebluenote.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.littlebluenote.chat.domain.ChatDomain;
import com.littlebluenote.chat.dto.RestrictionBody;
import com.littlebluenote.chat.entity.*;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.chat.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ChatAdminService {
    private static final Set<String> RESTRICTION_TYPES = Set.of(ChatDomain.CHAT_BAN, ChatDomain.FRIEND_BAN);

    private final FriendRelationMapper relationMapper;
    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatReportMapper reportMapper;
    private final ChatRestrictionMapper restrictionMapper;
    private final ChatOutboxMapper outboxMapper;
    private final UserDirectory users;

    public ChatAdminService(FriendRelationMapper relationMapper, ConversationMapper conversationMapper,
                            ChatMessageMapper messageMapper, ChatReportMapper reportMapper,
                            ChatRestrictionMapper restrictionMapper, ChatOutboxMapper outboxMapper,
                            UserDirectory users) {
        this.relationMapper = relationMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.restrictionMapper = restrictionMapper;
        this.outboxMapper = outboxMapper;
        this.users = users;
    }

    public Map<String, Object> stats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeFriendships", relationMapper.selectCount(
                new QueryWrapper<FriendRelation>().eq("status", ChatDomain.ACCEPTED)));
        stats.put("pendingFriendRequests", relationMapper.selectCount(
                new QueryWrapper<FriendRelation>().eq("status", ChatDomain.PENDING)));
        stats.put("activeConversations", conversationMapper.selectCount(
                new QueryWrapper<Conversation>().eq("status", ChatDomain.ACTIVE)));
        stats.put("messagesToday", messageMapper.selectCount(
                new QueryWrapper<ChatMessage>().ge("created_at", start)));
        stats.put("openReports", reportMapper.selectCount(
                new QueryWrapper<ChatReport>().eq("status", "OPEN")));
        stats.put("activeRestrictions", restrictionMapper.selectCount(
                new QueryWrapper<ChatRestriction>().eq("active", true)
                        .and(q -> q.isNull("ends_at").or().gt("ends_at", LocalDateTime.now()))));
        stats.put("outboxBacklog", outboxMapper.selectCount(
                new QueryWrapper<ChatOutbox>().in("status", "PENDING", "FAILED", "PROCESSING")));
        return stats;
    }

    public Map<String, Object> reports(int page, int size, String status) {
        QueryWrapper<ChatReport> query = new QueryWrapper<ChatReport>().orderByDesc("id");
        if (status != null && !status.isBlank()) query.eq("status", status.toUpperCase());
        IPage<ChatReport> result = reportMapper.selectPage(new Page<>(page, Math.min(Math.max(size, 1), 100)), query);
        return Map.of("total", result.getTotal(), "records", result.getRecords());
    }

    public Map<String, Object> restrictions(int page, int size) {
        IPage<ChatRestriction> result = restrictionMapper.selectPage(
                new Page<>(page, Math.min(Math.max(size, 1), 100)),
                new QueryWrapper<ChatRestriction>().orderByDesc("id"));
        return Map.of("total", result.getTotal(), "records", result.getRecords());
    }

    public Map<String, Object> outbox(int page, int size, String status) {
        QueryWrapper<ChatOutbox> query = new QueryWrapper<ChatOutbox>().orderByDesc("id");
        if (status != null && !status.isBlank()) query.eq("status", status.strip().toUpperCase());
        IPage<ChatOutbox> result = outboxMapper.selectPage(
                new Page<>(page, Math.min(Math.max(size, 1), 100)), query);
        return Map.of("total", result.getTotal(), "records", result.getRecords());
    }

    @Transactional
    public ChatOutbox retryOutbox(long outboxId) {
        ChatOutbox row = outboxMapper.selectById(outboxId);
        if (row == null) throw BusinessException.notFound("Outbox event not found");
        if ("PUBLISHED".equals(row.getStatus())) {
            throw BusinessException.conflict("Published events cannot be retried");
        }
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setNextRetryAt(LocalDateTime.now());
        row.setLastError(null);
        outboxMapper.updateById(row);
        return row;
    }

    @Transactional
    public ChatRestriction restrict(long adminId, RestrictionBody body) {
        String type = body.type().strip().toUpperCase();
        if (!RESTRICTION_TYPES.contains(type)) throw BusinessException.badRequest("Unsupported restriction type");
        users.require(body.userId());
        if (body.endsAt() != null && !body.endsAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("Restriction end time must be in the future");
        }
        ChatRestriction row = new ChatRestriction();
        row.setUserId(body.userId());
        row.setRestrictionType(type);
        row.setReason(body.reason().strip());
        row.setStartsAt(LocalDateTime.now());
        row.setEndsAt(body.endsAt());
        row.setOperatorAdminId(adminId);
        row.setActive(true);
        restrictionMapper.insert(row);
        return row;
    }

    @Transactional
    public void lift(long restrictionId) {
        ChatRestriction row = restrictionMapper.selectById(restrictionId);
        if (row == null) throw BusinessException.notFound("Restriction not found");
        row.setActive(false);
        restrictionMapper.updateById(row);
    }

    @Transactional
    public ChatReport resolve(long adminId, long reportId, String resolution) {
        ChatReport row = reportMapper.selectById(reportId);
        if (row == null) throw BusinessException.notFound("Report not found");
        row.setStatus("RESOLVED");
        row.setHandledBy(adminId);
        row.setResolution(resolution.strip());
        row.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(row);
        return row;
    }
}
