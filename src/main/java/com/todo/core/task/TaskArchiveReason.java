package com.todo.core.task;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskArchiveReason {
    UNFRIENDED,
    MANUAL_DELETE;

    public static TaskArchiveReason fromDatabase(String value) {
        return valueOf(value.toUpperCase());
    }

    @JsonValue
    public String databaseValue() {
        return name().toLowerCase();
    }
}
