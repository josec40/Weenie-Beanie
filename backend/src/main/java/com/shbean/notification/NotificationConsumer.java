package com.shbean.notification;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CredentialUpdated;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.Topics;
import com.shbean.loan.LoanProperties;
import com.shbean.notification.NotificationTemplates.Message;

/**
 * Domain events → in-app notifications. A plain listener (no retry topic) because some of these topics already have
 * their one retryable consumer; inserts are idempotent per event id.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EventPublisher events;
    private final NotificationService notifications;
    private final LoanProperties loans;

    public NotificationConsumer(EventPublisher events, NotificationService notifications, LoanProperties loans) {
        this.events = events;
        this.notifications = notifications;
        this.loans = loans;
    }

    @KafkaListener(id = "notification", groupId = "shbean-notification",
            topics = {Topics.OFFER_GENERATED, Topics.LOAN_DISBURSED, Topics.LOAN_REPAID, Topics.LOAN_FIAT_FAILED,
                    Topics.LOAN_DEFAULTED, Topics.CREDENTIAL_UPDATED})
    public void onEvent(String value) {
        EventEnvelope e = events.read(value);
        if (e.userId() == null) {
            return;
        }
        try {
            message(e).ifPresent(m -> notifications.add(e.eventId(), e.userId(), m.type(), m.title(), m.body(), m.link()));
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping notification for {} event {}: {}", e.type(), e.eventId(), ex.getMessage());
        }
    }

    private Optional<Message> message(EventEnvelope e) {
        return switch (e.type()) {
            case Topics.OFFER_GENERATED -> Optional.of(
                    NotificationTemplates.offer(events.payload(e, OfferGenerated.class)));
            case Topics.CREDENTIAL_UPDATED -> NotificationTemplates.credential(
                    events.payload(e, CredentialUpdated.class), loans::capFor);
            default -> NotificationTemplates.loan(e.type(), events.payload(e, LoanEvent.class));
        };
    }
}
