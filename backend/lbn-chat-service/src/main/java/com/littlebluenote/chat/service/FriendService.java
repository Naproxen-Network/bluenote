package com.littlebluenote.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.littlebluenote.chat.domain.ChatDomain;
import com.littlebluenote.chat.entity.*;
import com.littlebluenote.chat.exception.BusinessException;
import com.littlebluenote.chat.mapper.*;
import com.littlebluenote.chat.util.FriendPair;
import com.littlebluenote.common.dto.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FriendService {
    private final FriendRelationMapper relationMapper;
    private final FriendEventMapper eventMapper;
    private final UserBlockMapper blockMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final UserDirectory users;
    private final RestrictionService restrictions;
    private final RateLimitService rateLimits;
    private final OutboxService outbox;

    public FriendService(FriendRelationMapper relationMapper, FriendEventMapper eventMapper,
                         UserBlockMapper blockMapper, ConversationMapper conversationMapper,
                         ConversationMemberMapper memberMapper, UserDirectory users,
                         RestrictionService restrictions, RateLimitService rateLimits,
                         OutboxService outbox) {
        this.relationMapper = relationMapper;
        this.eventMapper = eventMapper;
        this.blockMapper = blockMapper;
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.users = users;
        this.restrictions = restrictions;
        this.rateLimits = rateLimits;
        this.outbox = outbox;
    }

    public long resolveTarget(Long targetUserId, String targetUsername) {
        boolean hasId = targetUserId != null;
        boolean hasUsername = targetUsername != null && !targetUsername.isBlank();
        if (hasId == hasUsername) {
            throw BusinessException.badRequest("Provide either targetUserId or targetUsername");
        }
        return hasId ? users.require(targetUserId).getId() : users.lookup(targetUsername).getId();
    }

    public Map<String, Object> lookupTarget(long userId, String identity) {
        UserDTO user = users.lookup(identity);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        if (Objects.equals(user.getId(), userId)) {
            result.put("status", "SELF");
            result.put("canAdd", false);
            result.put("canMessage", false);
            return result;
        }
        Map<String, Object> relationship = status(userId, user.getId());
        result.putAll(relationship);
        String status = String.valueOf(relationship.get("status"));
        result.put("canAdd", Set.of("NONE", ChatDomain.DELETED, ChatDomain.REJECTED,
                ChatDomain.CANCELLED).contains(status));
        return result;
    }

    @Transactional
    public Map<String, Object> request(long userId, long targetId, String message) {
        if (userId == targetId) throw BusinessException.badRequest("You cannot add yourself as a friend");
        restrictions.requireAllowed(userId, ChatDomain.FRIEND_BAN);
        rateLimits.checkFriendRequest(userId);
        users.require(targetId);
        FriendPair pair = FriendPair.of(userId, targetId);
        requireNotBlocked(userId, targetId);

        int inserted = relationMapper.ensurePair(pair.low(), pair.high(), userId, clean(message, 200));
        FriendRelation relation = relationMapper.selectPairForUpdate(pair.low(), pair.high());
        if (relation == null) throw new IllegalStateException("Friend pair was not persisted");

        // MySQL/JDBC may report one affected row for the no-op duplicate-key branch.
        // The persisted state, rather than the update count alone, must decide whether
        // this is a fresh pending request. Otherwise a DELETED/REJECTED relation can be
        // mistaken for a new row and never transition back to PENDING.
        if (inserted == 1 && ChatDomain.PENDING.equals(relation.getStatus())) {
            audit(relation, userId, targetId, "REQUESTED", message);
            notifyFriend(targetId, "FRIEND_REQUESTED", relation, userId);
            return present(relation, userId, Map.of(targetId, users.require(targetId)));
        }
        if (ChatDomain.ACCEPTED.equals(relation.getStatus())) {
            return present(relation, userId, Map.of(targetId, users.require(targetId)));
        }
        if (ChatDomain.PENDING.equals(relation.getStatus())) {
            if (Objects.equals(relation.getRequesterId(), userId)) {
                return present(relation, userId, Map.of(targetId, users.require(targetId)));
            }
            throw BusinessException.conflict("This user has already sent you a friend request");
        }

        relation.setRequesterId(userId);
        relation.setRequestMessage(clean(message, 200));
        relation.setStatus(ChatDomain.PENDING);
        relation.setRequestedAt(LocalDateTime.now());
        relation.setRespondedAt(null);
        relationMapper.updateById(relation);
        audit(relation, userId, targetId, "REQUESTED", message);
        notifyFriend(targetId, "FRIEND_REQUESTED", relation, userId);
        return present(relation, userId, Map.of(targetId, users.require(targetId)));
    }

    public List<Map<String, Object>> incoming(long userId) {
        List<FriendRelation> rows = relationMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("status", ChatDomain.PENDING).ne("requester_id", userId)
                .and(q -> q.eq("user_low_id", userId).or().eq("user_high_id", userId))
                .orderByDesc("requested_at"));
        return presentAll(rows, userId);
    }

    public List<Map<String, Object>> outgoing(long userId) {
        List<FriendRelation> rows = relationMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("status", ChatDomain.PENDING).eq("requester_id", userId)
                .orderByDesc("requested_at"));
        return presentAll(rows, userId);
    }

    public List<Map<String, Object>> friends(long userId) {
        List<FriendRelation> rows = relationMapper.selectList(new QueryWrapper<FriendRelation>()
                .eq("status", ChatDomain.ACCEPTED)
                .and(q -> q.eq("user_low_id", userId).or().eq("user_high_id", userId))
                .orderByDesc("responded_at"));
        return presentAll(rows, userId);
    }

    public Map<String, Object> status(long userId, long targetId) {
        FriendPair pair = FriendPair.of(userId, targetId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (isBlocked(userId, targetId)) {
            boolean byMe = blockMapper.selectCount(new QueryWrapper<UserBlock>()
                    .eq("blocker_id", userId).eq("blocked_id", targetId)) > 0;
            out.put("status", byMe ? "BLOCKED" : "BLOCKED_BY_PEER");
            out.put("canMessage", false);
            return out;
        }
        FriendRelation relation = relationMapper.selectOne(new QueryWrapper<FriendRelation>()
                .eq("user_low_id", pair.low()).eq("user_high_id", pair.high()));
        if (relation == null) {
            out.put("status", "NONE");
            out.put("canMessage", false);
            return out;
        }
        out.putAll(present(relation, userId, Map.of(targetId, users.require(targetId))));
        out.put("canMessage", ChatDomain.ACCEPTED.equals(relation.getStatus()));
        return out;
    }

    @Transactional
    public Map<String, Object> accept(long userId, long relationId) {
        FriendRelation relation = lockedRelation(relationId);
        long peer = peerAndRequireMember(relation, userId);
        if (!ChatDomain.PENDING.equals(relation.getStatus())) {
            if (ChatDomain.ACCEPTED.equals(relation.getStatus())) return conversationView(ensureConversation(relation));
            throw BusinessException.conflict("Friend request is no longer pending");
        }
        if (Objects.equals(relation.getRequesterId(), userId)) {
            throw BusinessException.forbidden("The requester cannot accept their own request");
        }
        requireNotBlocked(userId, peer);
        relation.setStatus(ChatDomain.ACCEPTED);
        relation.setRespondedAt(LocalDateTime.now());
        relationMapper.updateById(relation);
        Conversation conversation = ensureConversation(relation);
        audit(relation, userId, peer, "ACCEPTED", null);
        notifyFriend(peer, "FRIEND_ACCEPTED", relation, userId);

        Map<String, Object> out = conversationView(conversation);
        out.put("relation", present(relation, userId, Map.of(peer, users.require(peer))));
        return out;
    }

    @Transactional
    public void reject(long userId, long relationId) {
        transitionPending(userId, relationId, ChatDomain.REJECTED, "REJECTED", false);
    }

    @Transactional
    public void cancel(long userId, long relationId) {
        transitionPending(userId, relationId, ChatDomain.CANCELLED, "CANCELLED", true);
    }

    @Transactional
    public void remove(long userId, long targetId) {
        FriendPair pair = FriendPair.of(userId, targetId);
        FriendRelation relation = relationMapper.selectPairForUpdate(pair.low(), pair.high());
        if (relation == null || !ChatDomain.ACCEPTED.equals(relation.getStatus())) {
            throw BusinessException.conflict("Users are not active friends");
        }
        relation.setStatus(ChatDomain.DELETED);
        relation.setRespondedAt(LocalDateTime.now());
        relationMapper.updateById(relation);
        closeConversation(relation.getId());
        audit(relation, userId, targetId, "DELETED", null);
        notifyFriend(targetId, "FRIEND_DELETED", relation, userId);
    }

    @Transactional
    public void block(long userId, long targetId, String reason) {
        users.require(targetId);
        FriendPair pair = FriendPair.of(userId, targetId);
        blockMapper.upsert(userId, targetId, clean(reason, 255));
        FriendRelation relation = relationMapper.selectPairForUpdate(pair.low(), pair.high());
        if (relation != null) {
            relation.setStatus(ChatDomain.DELETED);
            relation.setRespondedAt(LocalDateTime.now());
            relationMapper.updateById(relation);
            closeConversation(relation.getId());
            audit(relation, userId, targetId, "BLOCKED", reason);
            notifyFriend(targetId, "FRIEND_BLOCKED", relation, userId);
        }
    }

    @Transactional
    public void unblock(long userId, long targetId) {
        blockMapper.delete(new QueryWrapper<UserBlock>()
                .eq("blocker_id", userId).eq("blocked_id", targetId));
    }

    public List<UserDTO> blocked(long userId) {
        List<Long> ids = blockMapper.selectList(new QueryWrapper<UserBlock>()
                .eq("blocker_id", userId).orderByDesc("id"))
                .stream().map(UserBlock::getBlockedId).toList();
        Map<Long, UserDTO> directory = users.batch(ids);
        return ids.stream().map(directory::get).filter(Objects::nonNull).toList();
    }

    @Transactional
    public Conversation openConversation(long userId, long targetId) {
        FriendRelation relation = requireAccepted(userId, targetId);
        requireNotBlocked(userId, targetId);
        return ensureConversation(relation);
    }

    public FriendRelation requireAccepted(long userId, long peerId) {
        FriendPair pair = FriendPair.of(userId, peerId);
        FriendRelation relation = relationMapper.selectOne(new QueryWrapper<FriendRelation>()
                .eq("user_low_id", pair.low()).eq("user_high_id", pair.high()));
        if (relation == null || !ChatDomain.ACCEPTED.equals(relation.getStatus())) {
            throw BusinessException.forbidden("Only active friends may chat");
        }
        return relation;
    }

    public boolean isBlocked(long first, long second) {
        return blockMapper.selectCount(new QueryWrapper<UserBlock>()
                .and(q -> q.eq("blocker_id", first).eq("blocked_id", second))
                .or(q -> q.eq("blocker_id", second).eq("blocked_id", first))) > 0;
    }

    private void requireNotBlocked(long first, long second) {
        if (isBlocked(first, second)) throw BusinessException.forbidden("Interaction is unavailable because one user blocked the other");
    }

    private FriendRelation lockedRelation(long relationId) {
        FriendRelation probe = relationMapper.selectById(relationId);
        if (probe == null) throw BusinessException.notFound("Friend request not found");
        FriendRelation locked = relationMapper.selectPairForUpdate(probe.getUserLowId(), probe.getUserHighId());
        if (locked == null) throw BusinessException.notFound("Friend request not found");
        return locked;
    }

    private long peerAndRequireMember(FriendRelation relation, long userId) {
        return new FriendPair(relation.getUserLowId(), relation.getUserHighId()).peerOf(userId);
    }

    private void transitionPending(long userId, long relationId, String nextStatus,
                                   String action, boolean requireRequester) {
        FriendRelation relation = lockedRelation(relationId);
        long peer = peerAndRequireMember(relation, userId);
        if (!ChatDomain.PENDING.equals(relation.getStatus())) {
            throw BusinessException.conflict("Friend request is no longer pending");
        }
        boolean requester = Objects.equals(relation.getRequesterId(), userId);
        if (requester != requireRequester) {
            throw BusinessException.forbidden(requireRequester
                    ? "Only the requester may cancel this request"
                    : "Only the recipient may reject this request");
        }
        relation.setStatus(nextStatus);
        relation.setRespondedAt(LocalDateTime.now());
        relationMapper.updateById(relation);
        audit(relation, userId, peer, action, null);
        notifyFriend(peer, "FRIEND_" + action, relation, userId);
    }

    private Conversation ensureConversation(FriendRelation relation) {
        Conversation conversation = conversationMapper.selectOne(new QueryWrapper<Conversation>()
                .eq("friend_relation_id", relation.getId()));
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setFriendRelationId(relation.getId());
            conversation.setType("PRIVATE");
            conversation.setStatus(ChatDomain.ACTIVE);
            conversationMapper.insert(conversation);
            addMember(conversation.getId(), relation.getUserLowId());
            addMember(conversation.getId(), relation.getUserHighId());
        } else if (!ChatDomain.ACTIVE.equals(conversation.getStatus())) {
            conversation.setStatus(ChatDomain.ACTIVE);
            conversationMapper.updateById(conversation);
        }
        return conversation;
    }

    private void addMember(long conversationId, long userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setUnreadCount(0);
        member.setMuted(false);
        member.setPinned(false);
        memberMapper.insert(member);
    }

    private void closeConversation(long relationId) {
        Conversation conversation = conversationMapper.selectOne(new QueryWrapper<Conversation>()
                .eq("friend_relation_id", relationId));
        if (conversation != null && !ChatDomain.CLOSED.equals(conversation.getStatus())) {
            conversation.setStatus(ChatDomain.CLOSED);
            conversationMapper.updateById(conversation);
        }
    }

    private void audit(FriendRelation relation, long actorId, long peerId, String action, String detail) {
        FriendEvent event = new FriendEvent();
        event.setRelationId(relation.getId());
        event.setActorId(actorId);
        event.setPeerId(peerId);
        event.setAction(action);
        event.setDetail(clean(detail, 500));
        eventMapper.insert(event);
    }

    private void notifyFriend(long recipientId, String kind, FriendRelation relation, long actorId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind);
        data.put("relationId", relation.getId());
        data.put("actorId", actorId);
        data.put("status", relation.getStatus());
        outbox.enqueue(recipientId, "friend-events", kind, relation.getId(), data);
    }

    private List<Map<String, Object>> presentAll(List<FriendRelation> rows, long viewerId) {
        List<Long> peerIds = rows.stream()
                .map(row -> new FriendPair(row.getUserLowId(), row.getUserHighId()).peerOf(viewerId))
                .toList();
        Map<Long, UserDTO> directory = users.batch(peerIds);
        return rows.stream().map(row -> present(row, viewerId, directory)).toList();
    }

    private Map<String, Object> present(FriendRelation relation, long viewerId, Map<Long, UserDTO> directory) {
        long peerId = new FriendPair(relation.getUserLowId(), relation.getUserHighId()).peerOf(viewerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", relation.getId());
        out.put("status", relation.getStatus());
        out.put("requesterId", relation.getRequesterId());
        out.put("requestMessage", relation.getRequestMessage());
        out.put("requestedAt", relation.getRequestedAt());
        out.put("respondedAt", relation.getRespondedAt());
        out.put("direction", Objects.equals(relation.getRequesterId(), viewerId) ? "OUTGOING" : "INCOMING");
        out.put("peer", directory.get(peerId));
        out.put("canMessage", ChatDomain.ACCEPTED.equals(relation.getStatus()));
        return out;
    }

    private Map<String, Object> conversationView(Conversation conversation) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversation.getId());
        out.put("status", conversation.getStatus());
        return out;
    }

    private String clean(String value, int max) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
