package com.todo.core.friendship;

import java.time.OffsetDateTime;

public record FriendRequest(
        long id,
        long userA,
        long userB,
        long requestedBy,
        FriendshipStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean involves(long userId) {
        return userA == userId || userB == userId;
    }
}
