package com.shbean.events;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fire-and-forget publisher: a Kafka outage must never fail the business operation that emitted the event
 * (send blocks at most {@code max.block.ms}). Failures are logged without the payload.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    /** Starts a new correlation chain. */
    public UUID publish(String type, UUID userId, Object payload) {
        return publish(type, userId, payload, null);
    }

    /** Continues the chain of {@code cause} (same correlationId). */
    public UUID publish(String type, UUID userId, Object payload, EventEnvelope cause) {
        UUID correlationId = cause != null ? cause.correlationId() : UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), type, userId, Instant.now(), correlationId,
                json.valueToTree(payload));
        String value;
        try {
            value = json.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event " + type, e);
        }
        try {
            kafka.send(type, userId == null ? null : userId.toString(), value).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.warn("Publishing {} {} failed: {}", type, envelope.eventId(), ex.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Publishing {} {} failed: {}", type, envelope.eventId(), e.getClass().getSimpleName());
        }
        return envelope.eventId();
    }

    public EventEnvelope read(String value) {
        try {
            return json.readValue(value, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return json.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed " + envelope.type() + " payload", e);
        }
    }
}
