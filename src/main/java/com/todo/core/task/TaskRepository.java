package com.todo.core.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {

    private static final RowMapper<Task> TASK_ROW_MAPPER = (rs, rowNum) -> new Task(
            rs.getLong("id"),
            rs.getLong("assigner_id"),
            rs.getLong("assignee_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("due_date", OffsetDateTime.class),
            TaskStatus.fromDatabase(rs.getString("status")),
            rs.getObject("archived_at", OffsetDateTime.class),
            rs.getString("archived_reason") == null ? null : TaskArchiveReason.fromDatabase(rs.getString("archived_reason")),
            rs.getObject("deleted_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public TaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Task create(long assignerId, long assigneeId, String title, String description, OffsetDateTime dueDate) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO tasks (assigner_id, assignee_id, title, description, due_date, status)
                        VALUES (?, ?, ?, ?, ?, 'todo')
                        RETURNING id, assigner_id, assignee_id, title, description, due_date, status,
                                  archived_at, archived_reason, deleted_at, created_at, updated_at
                        """, TASK_ROW_MAPPER, assignerId, assigneeId, title, description, dueDate);
    }

    public Optional<Task> findVisibleById(long id) {
        return jdbcTemplate.query("""
                        SELECT id, assigner_id, assignee_id, title, description, due_date, status,
                               archived_at, archived_reason, deleted_at, created_at, updated_at
                        FROM tasks
                        WHERE id = ? AND deleted_at IS NULL
                        """, TASK_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public List<Task> findVisibleByAssigneeId(long assigneeId) {
        return findVisibleByParticipant("assignee_id", assigneeId);
    }

    public List<Task> findVisibleByAssignerId(long assignerId) {
        return findVisibleByParticipant("assigner_id", assignerId);
    }

    public Task updateAssignerFields(long id, TaskUpdate update) {
        List<String> assignments = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (update.hasTitle()) {
            assignments.add("title = ?");
            arguments.add(update.title());
        }
        if (update.hasDescription()) {
            assignments.add("description = ?");
            arguments.add(update.description());
        }
        if (update.hasDueDate()) {
            assignments.add("due_date = ?");
            arguments.add(update.dueDate());
        }
        assignments.add("updated_at = CURRENT_TIMESTAMP");
        arguments.add(id);

        return jdbcTemplate.queryForObject("""
                        UPDATE tasks
                        SET %s
                        WHERE id = ?
                        RETURNING id, assigner_id, assignee_id, title, description, due_date, status,
                                  archived_at, archived_reason, deleted_at, created_at, updated_at
                        """.formatted(String.join(", ", assignments)), TASK_ROW_MAPPER, arguments.toArray());
    }

    public Task updateStatus(long id, TaskStatus status) {
        return jdbcTemplate.queryForObject("""
                        UPDATE tasks
                        SET status = CAST(? AS task_status), updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        RETURNING id, assigner_id, assignee_id, title, description, due_date, status,
                                  archived_at, archived_reason, deleted_at, created_at, updated_at
                        """, TASK_ROW_MAPPER, status.databaseValue(), id);
    }

    public void softDelete(long id) {
        jdbcTemplate.update("""
                        UPDATE tasks
                        SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, id);
    }

    private List<Task> findVisibleByParticipant(String column, long userId) {
        return jdbcTemplate.query("""
                        SELECT id, assigner_id, assignee_id, title, description, due_date, status,
                               archived_at, archived_reason, deleted_at, created_at, updated_at
                        FROM tasks
                        WHERE %s = ? AND deleted_at IS NULL
                        ORDER BY created_at DESC, id DESC
                        """.formatted(column), TASK_ROW_MAPPER, userId);
    }

    public record TaskUpdate(
            boolean hasTitle,
            String title,
            boolean hasDescription,
            String description,
            boolean hasDueDate,
            OffsetDateTime dueDate
    ) {
    }
}
