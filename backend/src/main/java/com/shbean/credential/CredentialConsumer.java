package com.shbean.credential;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.shbean.events.DeadLetters;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CredentialUpdateRequested;
import com.shbean.events.Topics;

/** {@code credential.update.requested} → reputation write (+ credential mint) → {@code credential.updated}. */
@Component
public class CredentialConsumer {

    static final String CONSUMER = "credential";

    private static final Logger log = LoggerFactory.getLogger(CredentialConsumer.class);

    private final CredentialService credentials;
    private final EventPublisher events;
    private final DeadLetters deadLetters;

    public CredentialConsumer(CredentialService credentials, EventPublisher events, DeadLetters deadLetters) {
        this.credentials = credentials;
        this.events = events;
        this.deadLetters = deadLetters;
    }

    @RetryableTopic(attempts = "${app.settlement.retry-attempts:5}",
            backoff = @Backoff(delayExpression = "${app.settlement.retry-delay-ms:15000}"),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = CONSUMER, groupId = "shbean-" + CONSUMER, topics = Topics.CREDENTIAL_UPDATE_REQUESTED,
            properties = {"max.poll.interval.ms=900000", "max.poll.records=5"})
    public void onUpdateRequested(String value) {
        EventEnvelope e = events.read(value);
        CredentialUpdateRequested req = events.payload(e, CredentialUpdateRequested.class);
        UUID userId = e.userId();
        try {
            credentials.apply(userId, req.loanId(), req.outcome(), req.amount())
                    .ifPresent(change -> events.publish(Topics.CREDENTIAL_UPDATED, userId, change.event(), e));
        } catch (RuntimeException ex) {
            log.warn("Credential update for loan {} failed, will retry: {}", req.loanId(), ex.getMessage());
            throw ex;
        }
    }

    @DltHandler
    public void onDeadLetter(String value) {
        deadLetters.record(CONSUMER, value);
    }
}
