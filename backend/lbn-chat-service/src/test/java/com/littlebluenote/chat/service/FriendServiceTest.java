package com.littlebluenote.chat.service;

import com.littlebluenote.chat.domain.ChatDomain;
import com.littlebluenote.chat.entity.FriendRelation;
import com.littlebluenote.chat.mapper.*;
import com.littlebluenote.common.dto.UserDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FriendServiceTest {
    @Test
    void deletedRelationReturnsToPendingEvenWhenMysqlReportsOneAffectedRow() {
        FriendRelationMapper relations = mock(FriendRelationMapper.class);
        FriendEventMapper events = mock(FriendEventMapper.class);
        UserBlockMapper blocks = mock(UserBlockMapper.class);
        ConversationMapper conversations = mock(ConversationMapper.class);
        ConversationMemberMapper members = mock(ConversationMemberMapper.class);
        UserDirectory users = mock(UserDirectory.class);
        RestrictionService restrictions = mock(RestrictionService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        OutboxService outbox = mock(OutboxService.class);
        FriendService service = new FriendService(relations, events, blocks, conversations, members,
                users, restrictions, rateLimits, outbox);

        FriendRelation deleted = new FriendRelation();
        deleted.setId(9L);
        deleted.setUserLowId(1L);
        deleted.setUserHighId(3L);
        deleted.setRequesterId(1L);
        deleted.setStatus(ChatDomain.DELETED);

        UserDTO peer = new UserDTO();
        peer.setId(3L);
        peer.setUsername("peer");
        peer.setDisplayName("Peer");
        when(users.require(3L)).thenReturn(peer);
        when(blocks.selectCount(any())).thenReturn(0L);
        when(relations.ensurePair(1L, 3L, 1L, "again")).thenReturn(1);
        when(relations.selectPairForUpdate(1L, 3L)).thenReturn(deleted);

        var result = service.request(1L, 3L, "again");

        assertEquals(ChatDomain.PENDING, deleted.getStatus());
        assertEquals(ChatDomain.PENDING, result.get("status"));
        assertEquals("again", deleted.getRequestMessage());
        verify(relations).updateById(deleted);
        verify(events).insert(any(com.littlebluenote.chat.entity.FriendEvent.class));
        verify(outbox).enqueue(eq(3L), eq("friend-events"), eq("FRIEND_REQUESTED"), eq(9L), any());
    }
}
