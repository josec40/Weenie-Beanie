package com.shbean.loan;

import java.util.UUID;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.shbean.events.DeadLetters;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CredentialUpdated;
import com.shbean.events.Events.ForecastUpdated;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.IdempotencyGuard;
import com.shbean.events.Topics;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.user.AppUserRepository;

/** forecast.updated or a tier change -> (re)evaluate the offer -> offer.generated when a new offer is created. */
@Component
public class OfferEventsConsumer {

    static final String CONSUMER = "offer";
    static final String TIER_CONSUMER = "offer-tier";

    private final EventPublisher events;
    private final IdempotencyGuard guard;
    private final DeadLetters deadLetters;
    private final OfferService offers;
    private final AppUserRepository users;

    public OfferEventsConsumer(EventPublisher events, IdempotencyGuard guard, DeadLetters deadLetters,
                               OfferService offers, AppUserRepository users) {
        this.events = events;
        this.guard = guard;
        this.deadLetters = deadLetters;
        this.offers = offers;
        this.users = users;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = CONSUMER, groupId = "shbean-" + CONSUMER, topics = Topics.FORECAST_UPDATED)
    public void onForecastUpdated(String value) {
        EventEnvelope e = events.read(value);
        if (e.userId() == null) {
            return;
        }
        refresh(CONSUMER, e, events.payload(e, ForecastUpdated.class).snapshotId());
    }

    /** A tier change moves the cap, but the forecast is unchanged, so forecast.updated would not fire. */
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = TIER_CONSUMER, groupId = "shbean-" + TIER_CONSUMER, topics = Topics.CREDENTIAL_UPDATED)
    public void onCredentialUpdated(String value) {
        EventEnvelope e = events.read(value);
        CredentialUpdated update = events.payload(e, CredentialUpdated.class);
        if (e.userId() != null && update.tier() != update.previousTier()) {
            refresh(TIER_CONSUMER, e, null);
        }
    }

    private void refresh(String consumer, EventEnvelope e, UUID snapshotId) {
        users.findById(e.userId()).ifPresent(user -> {
            Decision decision = offers.decide(user);
            String explanation = decision.proposal().map(offers::explain).orElse(null);
            guard.once(consumer, e.eventId(), () -> offers.apply(user.getId(), snapshotId, decision, explanation))
                    .filter(OfferService.Refresh::created)
                    .flatMap(OfferService.Refresh::open)
                    .ifPresent(o -> events.publish(Topics.OFFER_GENERATED, user.getId(), new OfferGenerated(o.getId(),
                            o.getShortfallMonth().toString(), o.getPrincipal(), o.getFee(), o.getRepaymentMonth().toString()), e));
        });
    }

    @DltHandler
    public void onDeadLetter(String value) {
        deadLetters.record(CONSUMER, value);
    }
}
