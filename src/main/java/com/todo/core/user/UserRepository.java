package com.todo.core.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private static final String USER_COLUMNS = "id, username, email, password_hash, created_at, updated_at";

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM users
                        WHERE email = ?
                        """.formatted(USER_COLUMNS), USER_ROW_MAPPER, email)
                .stream()
                .findFirst();
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM users
                        WHERE username = ?
                        """.formatted(USER_COLUMNS), USER_ROW_MAPPER, username)
                .stream()
                .findFirst();
    }

    public Optional<User> findById(long id) {
        return jdbcTemplate.query("""
                        SELECT %s
                        FROM users
                        WHERE id = ?
                        """.formatted(USER_COLUMNS), USER_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public Map<Long, String> findUsernamesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        Map<Long, String> usernames = new HashMap<>();
        jdbcTemplate.query(
                "SELECT id, username FROM users WHERE id IN (" + placeholders + ")",
                (rs, rowNum) -> {
                    usernames.put(rs.getLong("id"), rs.getString("username"));
                    return null;
                },
                ids.toArray());
        return usernames;
    }

    public User create(String username, String email, String passwordHash) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO users (username, email, password_hash)
                        VALUES (?, ?, ?)
                        RETURNING %s
                        """.formatted(USER_COLUMNS), USER_ROW_MAPPER, username, email, passwordHash);
    }
}
