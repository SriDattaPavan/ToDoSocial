package com.todo.core.task;

import java.time.OffsetDateTime;

public record Task(
        long id,
        long assignerId,
        long assigneeId,
        String title,
        String description,
        OffsetDateTime dueDate,
        TaskStatus status,
        OffsetDateTime archivedAt,
        TaskArchiveReason archivedReason,
        OffsetDateTime deletedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public boolean involves(long userId) {
        return assignerId == userId || assigneeId == userId;
    }
}
