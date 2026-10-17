package com.shbean.events;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs a consumer's handler at most once per (consumer, eventId); the marker commits with the handler's DB writes. */
@Component
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public IdempotencyGuard(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Returns empty if this consumer already processed the event; otherwise the handler's result. */
    public <T> Optional<T> once(String consumer, UUID eventId, Supplier<T> handler) {
        return tx.execute(status -> {
            int claimed = jdbc.update("""
                    INSERT INTO processed_event (consumer, event_id) VALUES (?, ?)
                    ON CONFLICT (consumer, event_id) DO NOTHING
                    """, consumer, eventId);
            return claimed == 0 ? Optional.<T>empty() : Optional.ofNullable(handler.get());
        });
    }
}
