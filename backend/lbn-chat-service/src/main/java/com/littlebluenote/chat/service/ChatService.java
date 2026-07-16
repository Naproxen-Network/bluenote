package com.littlebluenote.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.littlebluenote.chat.domain.ChatDomain;
import com.littlebluenote.chat.entity.*;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.chat.mapper.*;
import com.littlebluenote.chat.util.ChatInputPolicy;
import com.littlebluenote.common.dto.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChatService {
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatReportMapper reportMapper;
    private final FriendService friends;
    private final UserDirectory users;
    private final RestrictionService restrictions;
    private final RateLimitService rateLimits;
    private final OutboxService outbox;
    private final int maxMessageLength;

    public ChatService(ConversationMapper conversationMapper, ConversationMemberMapper memberMapper,
                       ChatMessageMapper messageMapper, ChatReportMapper reportMapper,
                       FriendService friends, UserDirectory users, RestrictionService restrictions,
                       RateLimitService rateLimits, OutboxService outbox,
                       @Value("${chat.message.max-length:2000}") int maxMessageLength) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.reportMapper = reportMapper;
        this.friends = friends;
        this.users = users;
        this.restrictions = restrictions;
        this.rateLimits = rateLimits;
        this.outbox = outbox;
        this.maxMessageLength = maxMessageLength;
    }

    @Transactional
    public Map<String, Object> open(long userId, long friendId) {
        Conversation conversation = friends.openConversation(userId, friendId);
        return presentConversation(conversation, userId);
    }

    public List<Map<String, Object>> conversations(long userId) {
        List<ConversationMember> memberships = memberMapper.selectList(new QueryWrapper<ConversationMember>()
                .eq("user_id", userId).orderByDesc("pinned").orderByDesc("updated_at"));
        List<Conversation> rows = memberships.stream()
                .map(m -> conversationMapper.selectById(m.getConversationId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Conversation::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return rows.stream().map(row -> presentConversation(row, userId)).toList();
    }

    public List<ChatMessage> history(long userId, long conversationId, Long beforeId, int limit) {
        ConversationMember member = requireMember(conversationId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        QueryWrapper<ChatMessage> query = new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conversationId).orderByDesc("id");
        if (beforeId != null) query.lt("id", beforeId);
        if (member.getDeletedBeforeId() != null) query.gt("id", member.getDeletedBeforeId());
        List<ChatMessage> descending = messageMapper.selectPage(new Page<>(1, safeLimit), query).getRecords();
        List<ChatMessage> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public ChatMessage send(long userId, long conversationId, String clientMessageId,
                            String rawContent, Long replyToId) {
        requireMember(conversationId, userId);
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !ChatDomain.ACTIVE.equals(conversation.getStatus())) {
            throw BusinessException.forbidden("Conversation is closed");
        }
        long peerId = peerOf(conversationId, userId);
        friends.requireAccepted(userId, peerId);
        if (friends.isBlocked(userId, peerId)) throw BusinessException.forbidden("Messaging is blocked");
        restrictions.requireAllowed(userId, ChatDomain.CHAT_BAN);

        String safeClientId = ChatInputPolicy.requireClientMessageId(clientMessageId);
        ChatMessage duplicate = messageMapper.selectOne(new QueryWrapper<ChatMessage>()
                .eq("sender_id", userId).eq("client_message_id", safeClientId));
        if (duplicate != null) return duplicate;

        rateLimits.checkMessage(userId);
        String content = ChatInputPolicy.requireText(rawContent, maxMessageLength);
        if (replyToId != null) {
            ChatMessage replied = messageMapper.selectById(replyToId);
            if (replied == null || !Objects.equals(replied.getConversationId(), conversationId)) {
                throw BusinessException.badRequest("Reply target does not belong to this conversation");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(userId);
        message.setClientMessageId(safeClientId);
        message.setMessageType("TEXT");
        message.setContent(content);
        message.setStatus(ChatDomain.NORMAL);
        message.setReplyToId(replyToId);
        message.setCreatedAt(now);
        if (messageMapper.insertIdempotent(message) != 1) {
            ChatMessage racedDuplicate = messageMapper.selectOne(new QueryWrapper<ChatMessage>()
                    .eq("sender_id", userId).eq("client_message_id", safeClientId));
            if (racedDuplicate != null) return racedDuplicate;
            throw new IllegalStateException("Message insert was ignored without an idempotent duplicate");
        }

        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(now);
        conversationMapper.updateById(conversation);
        if (memberMapper.incrementUnread(conversationId, peerId) != 1) {
            throw new IllegalStateException("Conversation recipient is missing");
        }

        Map<String, Object> event = messageEvent("MESSAGE", message);
        outbox.enqueue(peerId, "messages", "MESSAGE_CREATED", message.getId(), event);
        outbox.enqueue(userId, "messages", "MESSAGE_CREATED", message.getId(), event);
        return message;
    }

    @Transactional
    public Map<String, Object> markRead(long userId, long conversationId, Long requestedMessageId) {
        ConversationMember member = memberMapper.selectForUpdate(conversationId, userId);
        if (member == null) throw BusinessException.forbidden("Not a conversation member");
        Long readId = requestedMessageId;
        if (readId == null) {
            ChatMessage latest = messageMapper.selectOne(new QueryWrapper<ChatMessage>()
                    .eq("conversation_id", conversationId).orderByDesc("id").last("LIMIT 1"));
            readId = latest == null ? null : latest.getId();
        } else {
            ChatMessage target = messageMapper.selectById(readId);
            if (target == null || !Objects.equals(target.getConversationId(), conversationId)) {
                throw BusinessException.badRequest("Read position is outside this conversation");
            }
        }
        if (readId == null) return Map.of("conversationId", conversationId, "unreadCount", 0);

        long effective = member.getLastReadMessageId() == null
                ? readId : Math.max(member.getLastReadMessageId(), readId);
        long remaining = messageMapper.selectCount(new QueryWrapper<ChatMessage>()
                .eq("conversation_id", conversationId).gt("id", effective).ne("sender_id", userId));
        member.setLastReadMessageId(effective);
        member.setUnreadCount(Math.toIntExact(Math.min(remaining, Integer.MAX_VALUE)));
        memberMapper.updateById(member);

        long peerId = peerOf(conversationId, userId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("kind", "READ");
        event.put("conversationId", conversationId);
        event.put("readerId", userId);
        event.put("lastReadMessageId", effective);
        outbox.enqueue(peerId, "read-events", "MESSAGES_READ", conversationId, event);
        return event;
    }

    @Transactional
    public ChatMessage recall(long userId, long messageId) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) throw BusinessException.notFound("Message not found");
        if (!Objects.equals(message.getSenderId(), userId)) {
            throw BusinessException.forbidden("Only the sender may recall this message");
        }
        if (!ChatDomain.NORMAL.equals(message.getStatus())) return message;
        if (message.getCreatedAt() == null || Duration.between(message.getCreatedAt(), LocalDateTime.now()).toMinutes() >= 2) {
            throw BusinessException.conflict("Messages can only be recalled within two minutes");
        }
        message.setStatus(ChatDomain.RECALLED);
        message.setContent("");
        message.setRecalledAt(LocalDateTime.now());
        messageMapper.updateById(message);

        long peerId = peerOf(message.getConversationId(), userId);
        Map<String, Object> event = messageEvent("MESSAGE_RECALLED", message);
        outbox.enqueue(peerId, "messages", "MESSAGE_RECALLED", messageId, event);
        outbox.enqueue(userId, "messages", "MESSAGE_RECALLED", messageId, event);
        return message;
    }

    public long unreadTotal(long userId) {
        return memberMapper.selectList(new QueryWrapper<ConversationMember>().eq("user_id", userId))
                .stream().mapToLong(m -> m.getUnreadCount() == null ? 0 : m.getUnreadCount()).sum();
    }

    @Transactional
    public ChatReport report(long userId, long messageId, String type, String description) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) throw BusinessException.notFound("Message not found");
        requireMember(message.getConversationId(), userId);
        if (Objects.equals(message.getSenderId(), userId)) {
            throw BusinessException.badRequest("You cannot report your own message");
        }
        ChatReport existing = reportMapper.selectOne(new QueryWrapper<ChatReport>()
                .eq("reporter_id", userId).eq("message_id", messageId));
        if (existing != null) return existing;
        ChatReport report = new ChatReport();
        report.setReporterId(userId);
        report.setReportedUserId(message.getSenderId());
        report.setMessageId(messageId);
        report.setReportType(type.strip().toUpperCase(Locale.ROOT));
        report.setDescription(description == null ? null : description.strip());
        report.setStatus("OPEN");
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
        return report;
    }

    public long requireTypingTarget(long userId, long conversationId, long requestedTarget) {
        long peer = peerOf(conversationId, userId);
        if (peer != requestedTarget) throw BusinessException.forbidden("Invalid typing recipient");
        friends.requireAccepted(userId, peer);
        if (friends.isBlocked(userId, peer)) throw BusinessException.forbidden("Messaging is blocked");
        return peer;
    }

    private ConversationMember requireMember(long conversationId, long userId) {
        ConversationMember member = memberMapper.selectOne(new QueryWrapper<ConversationMember>()
                .eq("conversation_id", conversationId).eq("user_id", userId));
        if (member == null) throw BusinessException.forbidden("Not a conversation member");
        return member;
    }

    private long peerOf(long conversationId, long userId) {
        requireMember(conversationId, userId);
        ConversationMember peer = memberMapper.selectOne(new QueryWrapper<ConversationMember>()
                .eq("conversation_id", conversationId).ne("user_id", userId).last("LIMIT 1"));
        if (peer == null) throw new IllegalStateException("Private conversation has no peer");
        return peer.getUserId();
    }

    private Map<String, Object> presentConversation(Conversation conversation, long viewerId) {
        ConversationMember mine = requireMember(conversation.getId(), viewerId);
        long peerId = peerOf(conversation.getId(), viewerId);
        UserDTO peer = users.require(peerId);
        ChatMessage lastMessage = conversation.getLastMessageId() == null
                ? null : messageMapper.selectById(conversation.getLastMessageId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", conversation.getId());
        out.put("status", conversation.getStatus());
        out.put("peer", peer);
        out.put("lastMessage", lastMessage);
        out.put("lastMessageAt", conversation.getLastMessageAt());
        out.put("unreadCount", mine.getUnreadCount());
        out.put("lastReadMessageId", mine.getLastReadMessageId());
        out.put("muted", mine.getMuted());
        out.put("pinned", mine.getPinned());
        return out;
    }

    private Map<String, Object> messageEvent(String kind, ChatMessage message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("kind", kind);
        event.put("conversationId", message.getConversationId());
        event.put("message", message);
        return event;
    }
}
