package com.shbean.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.shbean.live.LiveEvents;
import com.shbean.live.LiveEvents.Kind;

@Service
public class NotificationService {

    public record Notification(UUID id, String type, String title, String body, String link, Instant createdAt,
                               boolean read) {
    }

    static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbc;
    private final LiveEvents live;

    public NotificationService(JdbcTemplate jdbc, LiveEvents live) {
        this.jdbc = jdbc;
        this.live = live;
    }

    /** Idempotent per source event; pushes the notification live only when it is new. */
    public void add(UUID eventId, UUID userId, String type, String title, String body, String link) {
        Instant now = Instant.now();
        int inserted = jdbc.update("""
                INSERT INTO notification (id, user_id, type, title, body, link, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, eventId, userId, type, title, body, link, Timestamp.from(now));
        if (inserted == 1) {
            live.emit(userId, Kind.NOTIFICATION, new Notification(eventId, type, title, body, link, now, false));
        }
    }

    public List<Notification> list(UUID userId, int limit) {
        return jdbc.query("""
                SELECT id, type, title, body, link, created_at, read_at FROM notification
                WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
                """, (rs, i) -> new Notification(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("title"), rs.getString("body"), rs.getString("link"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null),
                userId, Math.clamp(limit, 1, MAX_LIMIT));
    }

    public int unread(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND read_at IS NULL",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Empty or null {@code ids} marks all of the user's notifications read. */
    public int markRead(UUID userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return jdbc.update("UPDATE notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
        }
        List<UUID> capped = ids.stream().distinct().limit(MAX_LIMIT).toList();
        String placeholders = String.join(",", Collections.nCopies(capped.size(), "?"));
        Object[] args = new Object[capped.size() + 1];
        args[0] = userId;
        for (int i = 0; i < capped.size(); i++) {
            args[i + 1] = capped.get(i);
        }
        return jdbc.update("UPDATE notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL AND id IN ("
                + placeholders + ")", args);
    }
}
