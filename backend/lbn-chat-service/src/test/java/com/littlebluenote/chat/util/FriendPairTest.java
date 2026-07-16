package com.littlebluenote.chat.util;

import com.littlebluenote.chat.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendPairTest {

    @Test
    void canonicalizesBothDirectionsToOnePair() {
        FriendPair first = FriendPair.of(12, 3);
        FriendPair second = FriendPair.of(3, 12);

        assertEquals(first, second);
        assertEquals(3, first.low());
        assertEquals(12, first.high());
        assertEquals(12, first.peerOf(3));
        assertEquals(3, first.peerOf(12));
    }

    @Test
    void rejectsSelfFriendshipAndInvalidIds() {
        assertThrows(BusinessException.class, () -> FriendPair.of(7, 7));
        assertThrows(BusinessException.class, () -> FriendPair.of(0, 7));
        assertThrows(BusinessException.class, () -> FriendPair.of(7, -1));
    }

    @Test
    void rejectsNonMemberPeerLookup() {
        FriendPair pair = FriendPair.of(1, 2);
        assertThrows(BusinessException.class, () -> pair.peerOf(3));
    }
}
