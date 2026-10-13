package com.todo.core.user;

import java.time.OffsetDateTime;

public record User(
        long id,
        String username,
        String email,
        String passwordHash,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
