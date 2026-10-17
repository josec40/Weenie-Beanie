package com.shbean.settlement;

import java.time.YearMonth;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.events.DeadLetters;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Topics;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;

/**
 * Kafka entry points of the settlement sagas. A failing step throws, is retried via {@code <topic>.retry}, and after
 * the last attempt the DLT handler compensates. On-chain steps can wait for confirmations, hence the long poll interval.
 */
@Component
public class SettlementConsumer {

    static final String DISBURSE = "settle-disburse";
    static final String REPAY = "settle-repay";
    static final String DEFAULT = "settle-default";
    static final String SETTLEMENT_STUCK = "SETTLEMENT_STUCK";
    /** Settlement retries are slower than other consumers so RPC rate limits and devnet hiccups can clear. */
    static final String RETRY_ATTEMPTS = "${app.settlement.retry-attempts:5}";
    static final String RETRY_DELAY = "${app.settlement.retry-delay-ms:15000}";

    private static final Logger log = LoggerFactory.getLogger(SettlementConsumer.class);

    private final DisbursementSaga disbursement;
    private final RepaymentSaga repayment;
    private final ObjectProvider<LoanLedger> chain;
    private final LoanRepository loans;
    private final EventPublisher events;
    private final DeadLetters deadLetters;
    private final TransactionTemplate tx;

    public SettlementConsumer(DisbursementSaga disbursement, RepaymentSaga repayment, ObjectProvider<LoanLedger> chain,
                              LoanRepository loans, EventPublisher events, DeadLetters deadLetters,
                              TransactionTemplate tx) {
        this.disbursement = disbursement;
        this.repayment = repayment;
        this.chain = chain;
        this.loans = loans;
        this.events = events;
        this.deadLetters = deadLetters;
        this.tx = tx;
    }

    @RetryableTopic(attempts = RETRY_ATTEMPTS, backoff = @Backoff(delayExpression = RETRY_DELAY),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = DISBURSE, groupId = "shbean-" + DISBURSE, topics = Topics.LOAN_DISBURSEMENT_REQUESTED,
            properties = {"max.poll.interval.ms=900000", "max.poll.records=5"})
    public void onDisbursementRequested(String value) {
        EventEnvelope e = events.read(value);
        LoanEvent req = events.payload(e, LoanEvent.class);
        attempt("Disbursement", req, () -> disbursement.run(req.loanId(), YearMonth.parse(req.month()), e));
    }

    @RetryableTopic(attempts = RETRY_ATTEMPTS, backoff = @Backoff(delayExpression = RETRY_DELAY),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = REPAY, groupId = "shbean-" + REPAY, topics = Topics.LOAN_REPAYMENT_REQUESTED,
            properties = {"max.poll.interval.ms=900000", "max.poll.records=5"})
    public void onRepaymentRequested(String value) {
        EventEnvelope e = events.read(value);
        LoanEvent req = events.payload(e, LoanEvent.class);
        attempt("Repayment", req, () -> repayment.run(req.loanId(), YearMonth.parse(req.month()), e));
    }

    /** Records the default in the on-chain escrow (no-op in fiat-only mode or if the loan never reached the chain). */
    @RetryableTopic(attempts = RETRY_ATTEMPTS, backoff = @Backoff(delayExpression = RETRY_DELAY),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = DEFAULT, groupId = "shbean-" + DEFAULT, topics = Topics.LOAN_DEFAULTED,
            properties = {"max.poll.interval.ms=900000", "max.poll.records=5"})
    public void onDefaulted(String value) {
        LoanLedger ledger = chain.getIfAvailable();
        if (ledger == null) {
            return;
        }
        LoanEvent req = events.payload(events.read(value), LoanEvent.class);
        attempt("Default", req, () -> loans.findById(req.loanId())
                .filter(l -> l.getStatus() == Loan.Status.DEFAULTED)
                .ifPresent(ledger::markDefault));
    }

    private static void attempt(String what, LoanEvent req, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            log.warn("{} of loan {} failed, will retry: {}", what, req.loanId(), ex.getMessage());
            throw ex;
        }
    }

    @DltHandler
    public void onDeadLetter(String value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        deadLetters.record("settlement", value);
        UUID loanId = null;
        try {
            EventEnvelope e = events.read(value);
            LoanEvent req = events.payload(e, LoanEvent.class);
            loanId = req.loanId();
            YearMonth month = YearMonth.parse(req.month());
            if (topic.startsWith(Topics.LOAN_DISBURSEMENT_REQUESTED)) {
                disbursement.giveUp(loanId, month, e);
            } else if (topic.startsWith(Topics.LOAN_REPAYMENT_REQUESTED)) {
                repayment.compensate(loanId, month, e);
            }
        } catch (RuntimeException ex) {
            log.error("Settlement compensation failed for loan {} ({}): {}", loanId, topic, ex.getMessage());
            if (loanId != null) {
                flagStuck(loanId);
            }
        }
    }

    private void flagStuck(UUID loanId) {
        try {
            tx.executeWithoutResult(s -> loans.findById(loanId).ifPresent(l -> l.settlementProblem(SETTLEMENT_STUCK)));
        } catch (RuntimeException ex) {
            log.error("Could not flag loan {} as stuck: {}", loanId, ex.getMessage());
        }
    }
}
