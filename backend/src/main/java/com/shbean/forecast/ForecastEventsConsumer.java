package com.shbean.forecast;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
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
import com.shbean.events.Events.ForecastUpdated;
import com.shbean.events.IdempotencyGuard;
import com.shbean.events.Topics;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastService.Forecast;
import com.shbean.user.AppUserRepository;

/** cashflow.changed -> recompute 12-month forecast -> forecast_snapshot (if changed) -> forecast.updated. */
@Component
public class ForecastEventsConsumer {

    static final String CONSUMER = "forecast";
    static final int HORIZON = 12;

    private final EventPublisher events;
    private final IdempotencyGuard guard;
    private final DeadLetters deadLetters;
    private final ForecastService forecasts;
    private final ForecastSnapshotStore snapshots;
    private final AppUserRepository users;

    public ForecastEventsConsumer(EventPublisher events, IdempotencyGuard guard, DeadLetters deadLetters,
                                  ForecastService forecasts, ForecastSnapshotStore snapshots, AppUserRepository users) {
        this.events = events;
        this.guard = guard;
        this.deadLetters = deadLetters;
        this.forecasts = forecasts;
        this.snapshots = snapshots;
        this.users = users;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = CONSUMER, groupId = "shbean-" + CONSUMER, topics = Topics.CASHFLOW_CHANGED)
    public void onCashflowChanged(String value) {
        EventEnvelope e = events.read(value);
        if (e.userId() == null) {
            return;
        }
        guard.once(CONSUMER, e.eventId(), () -> users.findById(e.userId())
                        .flatMap(user -> {
                            Forecast f = forecasts.forecast(user, HORIZON);
                            return snapshots.saveIfChanged(user.getId(), f).map(id -> updated(id, f));
                        })
                        .orElse(null))
                .ifPresent(payload -> events.publish(Topics.FORECAST_UPDATED, e.userId(), payload, e));
    }

    @DltHandler
    public void onDeadLetter(String value) {
        deadLetters.record(CONSUMER, value);
    }

    private static ForecastUpdated updated(UUID snapshotId, Forecast f) {
        List<String> shortfalls = f.months().stream().filter(Month::shortfall).map(m -> m.month().toString()).toList();
        BigDecimal lowest = f.months().stream().map(Month::endingBalance).min(Comparator.naturalOrder())
                .orElse(f.startingBalance());
        return new ForecastUpdated(snapshotId, f.currentMonth().toString(), f.startingBalance(), shortfalls, lowest);
    }
}
