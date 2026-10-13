package com.todo.core.friendship;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FriendshipStatus {
    PENDING,
    ACCEPTED,
    REMOVED;

    @JsonValue
    public String databaseValue() {
        return name().toLowerCase();
    }

    public static FriendshipStatus fromDatabase(String value) {
        return valueOf(value.toUpperCase());
    }
}
