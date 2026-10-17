package com.shbean.live;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.shbean.credential.CredentialController;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.OfferGenerated;
import com.shbean.events.Events.TransactionsIngested;
import com.shbean.events.Topics;
import com.shbean.live.LiveEvents.Kind;
import com.shbean.loan.LoanOfferRepository;
import com.shbean.loan.LoanRepository;

/**
 * Kafka → subscriptions. Every instance joins with its own group and reads only new records, so each instance sees
 * every event for the sockets it holds. Pushes the current entity (re-read after the event's commit), never the payload.
 */
@Component
public class LiveEventsBridge {

    public record Balance(BigDecimal balance, Instant asOf) {
    }

    private static final Logger log = LoggerFactory.getLogger(LiveEventsBridge.class);

    private final EventPublisher events;
    private final LiveEvents live;
    private final LoanRepository loans;
    private final LoanOfferRepository offers;
    private final CredentialController credentials;

    public LiveEventsBridge(EventPublisher events, LiveEvents live, LoanRepository loans, LoanOfferRepository offers,
                            CredentialController credentials) {
        this.events = events;
        this.live = live;
        this.loans = loans;
        this.offers = offers;
        this.credentials = credentials;
    }

    @KafkaListener(id = "live", groupId = "shbean-live-#{T(java.util.UUID).randomUUID()}",
            properties = "auto.offset.reset=latest",
            topics = {Topics.LOAN_ACCEPTED, Topics.LOAN_DISBURSEMENT_REQUESTED, Topics.LOAN_DISBURSED,
                    Topics.LOAN_REPAYMENT_REQUESTED, Topics.LOAN_REPAID, Topics.LOAN_DEFAULTED, Topics.LOAN_FIAT_SETTLED,
                    Topics.LOAN_FIAT_FAILED, Topics.OFFER_GENERATED, Topics.CREDENTIAL_UPDATED,
                    Topics.TRANSACTIONS_INGESTED})
    public void onEvent(String value) {
        try {
            EventEnvelope e = events.read(value);
            if (e.userId() == null) {
                return;
            }
            switch (e.type()) {
                case Topics.OFFER_GENERATED -> offers.findById(events.payload(e, OfferGenerated.class).offerId())
                        .ifPresent(o -> live.emit(e.userId(), Kind.OFFER, o));
                case Topics.CREDENTIAL_UPDATED -> live.emit(e.userId(), Kind.CREDENTIAL, credentials.view(e.userId()));
                case Topics.TRANSACTIONS_INGESTED -> live.emit(e.userId(), Kind.BALANCE,
                        new Balance(events.payload(e, TransactionsIngested.class).balance(), e.occurredAt()));
                default -> loans.findById(events.payload(e, LoanEvent.class).loanId())
                        .ifPresent(l -> live.emit(e.userId(), Kind.LOAN, l));
            }
        } catch (RuntimeException ex) {
            // Live updates are best effort; clients also refetch on reconnect and on a slow fallback poll.
            log.debug("Live update skipped: {}", ex.getMessage());
        }
    }
}
