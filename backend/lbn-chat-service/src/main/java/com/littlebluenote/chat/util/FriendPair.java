package com.littlebluenote.chat.util;

import com.littlebluenote.chat.exception.BusinessException;

public record FriendPair(long low, long high) {
    public static FriendPair of(long first, long second) {
        if (first <= 0 || second <= 0) throw BusinessException.badRequest("Invalid user id");
        if (first == second) throw BusinessException.badRequest("You cannot add yourself as a friend");
        return first < second ? new FriendPair(first, second) : new FriendPair(second, first);
    }

    public long peerOf(long userId) {
        if (userId == low) return high;
        if (userId == high) return low;
        throw BusinessException.forbidden("User is not part of this friendship");
    }
}
