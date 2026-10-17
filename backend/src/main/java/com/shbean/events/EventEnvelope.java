package com.shbean.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/** Wire format for every Kafka event. The topic name equals {@code type}; the record key is {@code userId}. */
public record EventEnvelope(UUID eventId, String type, UUID userId, Instant occurredAt, UUID correlationId,
                            JsonNode payload) {
}
