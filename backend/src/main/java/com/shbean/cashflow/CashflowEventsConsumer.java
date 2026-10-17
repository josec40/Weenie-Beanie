package com.shbean.cashflow;

import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.shbean.events.DeadLetters;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.IdempotencyGuard;
import com.shbean.events.Topics;

/** transactions.ingested -> cashflow.changed. */
@Component
public class CashflowEventsConsumer {

    static final String CONSUMER = "cashflow";

    private final EventPublisher events;
    private final IdempotencyGuard guard;
    private final DeadLetters deadLetters;

    public CashflowEventsConsumer(EventPublisher events, IdempotencyGuard guard, DeadLetters deadLetters) {
        this.events = events;
        this.guard = guard;
        this.deadLetters = deadLetters;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = CONSUMER, groupId = "shbean-" + CONSUMER, topics = Topics.TRANSACTIONS_INGESTED)
    public void onTransactionsIngested(String value) {
        EventEnvelope e = events.read(value);
        guard.once(CONSUMER, e.eventId(), () -> Boolean.TRUE)
                .ifPresent(x -> events.publish(Topics.CASHFLOW_CHANGED, e.userId(),
                        new CashflowChanged(CashflowReason.TRANSACTIONS), e));
    }

    @DltHandler
    public void onDeadLetter(String value) {
        deadLetters.record(CONSUMER, value);
    }
}
