package com.shbean.events;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Append-only audit trail of every event; idempotent because audit_event is keyed by eventId. */
@Component
public class AuditConsumer {

    private final JdbcTemplate jdbc;
    private final EventPublisher events;

    public AuditConsumer(JdbcTemplate jdbc, EventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @KafkaListener(id = "audit", groupId = "shbean-audit", topics = "#{T(com.shbean.events.Topics).ALL}")
    public void onEvent(String value) {
        EventEnvelope e = events.read(value);
        record(e);
    }

    public void record(EventEnvelope e) {
        jdbc.update("""
                INSERT INTO audit_event (id, occurred_at, user_id, type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id, occurred_at) DO NOTHING
                """, e.eventId(), Timestamp.from(e.occurredAt()), e.userId(), e.type(),
                "{\"correlationId\":\"%s\",\"data\":%s}".formatted(e.correlationId(), e.payload()));
    }
}
