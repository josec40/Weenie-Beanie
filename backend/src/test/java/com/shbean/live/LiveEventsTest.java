package com.shbean.live;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shbean.live.LiveEvents.Kind;

import reactor.test.StepVerifier;

class LiveEventsTest {

    @Test
    void subscribersOnlySeeTheirOwnEventsOfTheRequestedKind() {
        LiveEvents live = new LiveEvents();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        StepVerifier.create(live.stream(alice, Kind.NOTIFICATION, String.class).take(2))
                .then(() -> {
                    live.emit(bob, Kind.NOTIFICATION, "for bob");
                    live.emit(alice, Kind.LOAN, "loan");
                    live.emit(alice, Kind.NOTIFICATION, "first");
                    live.emit(alice, Kind.NOTIFICATION, "second");
                })
                .expectNext("first", "second")
                .verifyComplete();
    }

    @Test
    void emittingWithoutSubscribersDoesNotFail() {
        new LiveEvents().emit(UUID.randomUUID(), Kind.BALANCE, "ignored");
    }
}
