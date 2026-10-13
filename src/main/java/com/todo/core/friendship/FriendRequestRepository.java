package com.todo.core.friendship;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class FriendRequestRepository {

    private static final RowMapper<FriendRequest> FRIEND_REQUEST_ROW_MAPPER = (rs, rowNum) -> new FriendRequest(
            rs.getLong("id"),
            rs.getLong("user_a"),
            rs.getLong("user_b"),
            rs.getLong("requested_by"),
            FriendshipStatus.fromDatabase(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public FriendRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<FriendRequest> createOrRerequest(long senderId, long receiverId) {
        long userA = Math.min(senderId, receiverId);
        long userB = Math.max(senderId, receiverId);
        return jdbcTemplate.query("""
                        INSERT INTO friend_requests (user_a, user_b, requested_by, status)
                        VALUES (?, ?, ?, 'pending')
                        ON CONFLICT (user_a, user_b) DO UPDATE SET
                            requested_by = EXCLUDED.requested_by,
                            status = 'pending',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE friend_requests.status = 'removed'
                        RETURNING id, user_a, user_b, requested_by, status, created_at, updated_at
                        """, FRIEND_REQUEST_ROW_MAPPER, userA, userB, senderId)
                .stream()
                .findFirst();
    }

    public Optional<FriendRequest> findByIdForUpdate(long id) {
        return jdbcTemplate.query("""
                        SELECT id, user_a, user_b, requested_by, status, created_at, updated_at
                        FROM friend_requests
                        WHERE id = ?
                        FOR UPDATE
                        """, FRIEND_REQUEST_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public Optional<FriendRequest> findByPairForUpdate(long firstUserId, long secondUserId) {
        return jdbcTemplate.query("""
                        SELECT id, user_a, user_b, requested_by, status, created_at, updated_at
                        FROM friend_requests
                        WHERE user_a = ? AND user_b = ?
                        FOR UPDATE
                        """, FRIEND_REQUEST_ROW_MAPPER, Math.min(firstUserId, secondUserId), Math.max(firstUserId, secondUserId))
                .stream()
                .findFirst();
    }

    public List<FriendRequest> findIncomingPending(long currentUserId) {
        return jdbcTemplate.query("""
                        SELECT id, user_a, user_b, requested_by, status, created_at, updated_at
                        FROM friend_requests
                        WHERE status = 'pending'
                          AND requested_by <> ?
                          AND (user_a = ? OR user_b = ?)
                        ORDER BY created_at DESC, id DESC
                        """, FRIEND_REQUEST_ROW_MAPPER, currentUserId, currentUserId, currentUserId);
    }

    public List<FriendRequest> findOutgoingPending(long currentUserId) {
        return jdbcTemplate.query("""
                        SELECT id, user_a, user_b, requested_by, status, created_at, updated_at
                        FROM friend_requests
                        WHERE status = 'pending'
                          AND requested_by = ?
                        ORDER BY created_at DESC, id DESC
                        """, FRIEND_REQUEST_ROW_MAPPER, currentUserId);
    }

    public FriendRequest changeStatus(long id, FriendshipStatus status) {
        return jdbcTemplate.queryForObject("""
                        UPDATE friend_requests
                        SET status = CAST(? AS friendship_status), updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        RETURNING id, user_a, user_b, requested_by, status, created_at, updated_at
                        """, FRIEND_REQUEST_ROW_MAPPER, status.databaseValue(), id);
    }

    public int archiveActiveTasksBetween(long firstUserId, long secondUserId) {
        return jdbcTemplate.update("""
                        UPDATE tasks
                        SET status = 'archived',
                            archived_at = CURRENT_TIMESTAMP,
                            archived_reason = 'unfriended',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE deleted_at IS NULL
                          AND status <> 'archived'
                          AND ((assigner_id = ? AND assignee_id = ?)
                               OR (assigner_id = ? AND assignee_id = ?))
                        """, firstUserId, secondUserId, secondUserId, firstUserId);
    }
}
