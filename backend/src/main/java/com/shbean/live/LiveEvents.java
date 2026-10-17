package com.shbean.live;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process fan-out of per-user updates to GraphQL subscriptions. Best effort: a slow subscriber drops updates
 * instead of blocking others, and clients refetch on reconnect.
 */
@Component
public class LiveEvents {

    public enum Kind { LOAN, OFFER, CREDENTIAL, BALANCE, NOTIFICATION }

    record LiveEvent(UUID userId, Kind kind, Object payload) {
    }

    private final Sinks.Many<LiveEvent> sink = Sinks.many().multicast().directBestEffort();

    public void emit(UUID userId, Kind kind, Object payload) {
        if (userId != null && payload != null) {
            sink.emitNext(new LiveEvent(userId, kind, payload), Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
        }
    }

    public <T> Flux<T> stream(UUID userId, Kind kind, Class<T> type) {
        return sink.asFlux()
                .filter(e -> e.kind() == kind && e.userId().equals(userId))
                .map(e -> type.cast(e.payload()));
    }
}
