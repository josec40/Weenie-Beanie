package com.shbean.loan;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.events.DeadLetters;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.IdempotencyGuard;
import com.shbean.events.Topics;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/**
 * Fiat settlement through Nessie. Phase 7 extends these steps with the Solana escrow (on-chain first, then Nessie).
 * A Nessie failure rolls back the idempotency marker so the event is retried, then dead-lettered.
 */
@Component
public class LoanSettlementConsumer {

    static final String DISBURSE = "settle-disburse";
    static final String REPAY = "settle-repay";
    static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    private static final Logger log = LoggerFactory.getLogger(LoanSettlementConsumer.class);

    record Outcome(Loan loan, AppUser user, boolean settled) {
    }

    private final LoanRepository loans;
    private final AppUserRepository users;
    private final NessieClient nessie;
    private final NessieSyncService sync;
    private final TransactionStore store;
    private final LoanProperties props;
    private final EventPublisher events;
    private final IdempotencyGuard guard;
    private final DeadLetters deadLetters;

    public LoanSettlementConsumer(LoanRepository loans, AppUserRepository users, NessieClient nessie,
                                  NessieSyncService sync, TransactionStore store, LoanProperties props,
                                  EventPublisher events, IdempotencyGuard guard, DeadLetters deadLetters) {
        this.loans = loans;
        this.users = users;
        this.nessie = nessie;
        this.sync = sync;
        this.store = store;
        this.props = props;
        this.events = events;
        this.guard = guard;
        this.deadLetters = deadLetters;
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = DISBURSE, groupId = "shbean-" + DISBURSE, topics = Topics.LOAN_DISBURSEMENT_REQUESTED)
    public void onDisbursementRequested(String value) {
        EventEnvelope e = events.read(value);
        LoanEvent req = events.payload(e, LoanEvent.class);
        YearMonth month = YearMonth.parse(req.month());

        Optional<Outcome> outcome = guard.once(DISBURSE, e.eventId(), () -> loans.findById(req.loanId())
                .filter(l -> l.getStatus() == Loan.Status.DISBURSING)
                .map(l -> {
                    AppUser user = users.findById(l.getUserId()).orElseThrow();
                    String depositId = nessie.createDeposit(user.getNessieCheckingAccountId(), new CreateTransferRequest(
                            "balance", month.atDay(1), "completed", l.getPrincipal(), "ShBean micro-loan disbursement")).id();
                    l.disbursed(depositId);
                    return new Outcome(loans.save(l), user, true);
                })
                .orElse(null));

        outcome.ifPresent(o -> {
            syncQuietly(o.user());
            LoanEvent done = LoanService.event(o.loan(), month, null);
            events.publish(Topics.LOAN_FIAT_SETTLED, o.user().getId(), done, e);
            events.publish(Topics.LOAN_DISBURSED, o.user().getId(), done, e);
        });
    }

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            retryTopicSuffix = ".retry", dltTopicSuffix = ".dlt", numPartitions = "3", replicationFactor = "1")
    @KafkaListener(id = REPAY, groupId = "shbean-" + REPAY, topics = Topics.LOAN_REPAYMENT_REQUESTED)
    public void onRepaymentRequested(String value) {
        EventEnvelope e = events.read(value);
        LoanEvent req = events.payload(e, LoanEvent.class);
        YearMonth month = YearMonth.parse(req.month());

        Optional<Outcome> outcome = guard.once(REPAY, e.eventId(), () -> loans.findById(req.loanId())
                .filter(l -> l.getStatus() == Loan.Status.REPAYING)
                .map(l -> {
                    AppUser user = users.findById(l.getUserId()).orElseThrow();
                    BigDecimal balance = store.latestBalance(user.getNessieCheckingAccountId())
                            .map(TransactionStore.BalanceSnapshot::balance).orElse(BigDecimal.ZERO);
                    // Safeguard: never auto-draft into (or near) an overdraft.
                    if (balance.subtract(l.getTotalDue()).compareTo(props.minBufferAfterRepay()) < 0) {
                        l.late(INSUFFICIENT_FUNDS, month.plusMonths(props.graceMonths()));
                        return new Outcome(loans.save(l), user, false);
                    }
                    String withdrawalId = nessie.createWithdrawal(user.getNessieCheckingAccountId(), new CreateTransferRequest(
                            "balance", month.atEndOfMonth(), "completed", l.getTotalDue(), "ShBean micro-loan repayment")).id();
                    l.repaid(withdrawalId);
                    return new Outcome(loans.save(l), user, true);
                })
                .orElse(null));

        outcome.ifPresent(o -> {
            if (o.settled()) {
                syncQuietly(o.user());
                LoanEvent done = LoanService.event(o.loan(), month, null);
                events.publish(Topics.LOAN_FIAT_SETTLED, o.user().getId(), done, e);
                events.publish(Topics.LOAN_REPAID, o.user().getId(), done, e);
            } else {
                events.publish(Topics.LOAN_FIAT_FAILED, o.user().getId(), LoanService.event(o.loan(), month, INSUFFICIENT_FUNDS), e);
            }
        });
    }

    @DltHandler
    public void onDeadLetter(String value) {
        deadLetters.record("settlement", value);
    }

    private void syncQuietly(AppUser user) {
        try {
            sync.sync(user);
        } catch (RuntimeException ex) {
            log.warn("Post-settlement sync failed for user {}: {}", user.getId(), ex.getMessage());
        }
    }
}
