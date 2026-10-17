package com.shbean.events;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Records events that exhausted their retries so they are visible in audit_event (type EVENT_DEAD_LETTERED). */
@Component
public class DeadLetters {

    private static final Logger log = LoggerFactory.getLogger(DeadLetters.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DeadLetters(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(String consumer, String value) {
        ObjectNode payload = json.createObjectNode().put("consumer", consumer);
        UUID userId = null;
        try {
            EventEnvelope e = json.readValue(value, EventEnvelope.class);
            payload.put("eventId", String.valueOf(e.eventId())).put("type", e.type());
            userId = e.userId();
        } catch (Exception ex) {
            payload.put("unparseable", true);
        }
        log.error("Event dead-lettered by consumer {}: {}", consumer, payload);
        jdbc.update("INSERT INTO audit_event (id, occurred_at, user_id, type, payload) VALUES (?, now(), ?, 'EVENT_DEAD_LETTERED', ?::jsonb)",
                UUID.randomUUID(), userId, payload.toString());
    }
}
