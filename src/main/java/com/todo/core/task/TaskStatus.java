package com.todo.core.task;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    ARCHIVED;

    @JsonValue
    public String databaseValue() {
        return name().toLowerCase();
    }

    public static TaskStatus fromDatabase(String value) {
        return valueOf(value.toUpperCase());
    }

    public static TaskStatus fromRequest(String value) {
        return fromDatabase(value);
    }
}
