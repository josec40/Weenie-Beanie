package com.shbean.settlement;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Topics;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/**
 * Disbursement: [on-chain create_loan → disburse] → Nessie deposit → ACTIVE. The chain goes first so fiat only
 * moves once the escrow has paid out. Without {@code solana.enabled} the on-chain part is skipped (fiat-only).
 */
@Component
public class DisbursementSaga {

    static final String DEPOSIT = "DEPOSIT_FIAT";
    public static final String SETTLEMENT_FAILED = "SETTLEMENT_FAILED";

    private static final Logger log = LoggerFactory.getLogger(DisbursementSaga.class);

    private final LoanRepository loans;
    private final AppUserRepository users;
    private final ObjectProvider<LoanLedger> chain;
    private final NessieLedger fiat;
    private final NessieSyncService sync;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    public DisbursementSaga(LoanRepository loans, AppUserRepository users, ObjectProvider<LoanLedger> chain,
                            NessieLedger fiat, NessieSyncService sync, EventPublisher events, TransactionTemplate tx) {
        this.loans = loans;
        this.users = users;
        this.chain = chain;
        this.fiat = fiat;
        this.sync = sync;
        this.events = events;
        this.tx = tx;
    }

    public void run(UUID loanId, YearMonth month, EventEnvelope cause) {
        Optional<Loan> pending = loans.findById(loanId).filter(l -> l.getStatus() == Loan.Status.DISBURSING);
        if (pending.isEmpty()) {
            return;
        }
        Loan loan = pending.get();
        AppUser user = users.findById(loan.getUserId()).orElseThrow();

        LoanLedger ledger = chain.getIfAvailable();
        String signature = null;
        if (ledger != null) {
            ledger.open(loan);
            signature = ledger.disburse(loan);
        }
        String depositId = fiat.deposit(loanId, DEPOSIT, user.getNessieCheckingAccountId(), month.atDay(1),
                loan.getPrincipal(), NessieLedger.disbursementDescription(loanId));

        String disburseSig = signature;
        Loan done = tx.execute(s -> loans.findById(loanId)
                .filter(l -> l.getStatus() == Loan.Status.DISBURSING)
                .map(l -> {
                    l.disbursed(depositId, disburseSig);
                    return l;
                })
                .orElse(null));
        if (done == null) {
            return;
        }
        syncQuietly(sync, user);
        LoanEvent event = LoanService.event(done, month, null);
        events.publish(Topics.LOAN_FIAT_SETTLED, user.getId(), event, cause);
        events.publish(Topics.LOAN_DISBURSED, user.getId(), event, cause);
    }

    /**
     * Retries are exhausted. Steps that already happened cannot be reversed automatically (the escrow may have paid
     * out), so the loan stays DISBURSING, is flagged, and the saga resumes where it stopped if the event is replayed.
     */
    public void giveUp(UUID loanId, YearMonth month, EventEnvelope cause) {
        Loan flagged = tx.execute(s -> loans.findById(loanId)
                .filter(l -> l.getStatus() == Loan.Status.DISBURSING)
                .map(l -> {
                    l.settlementProblem(SETTLEMENT_FAILED);
                    return l;
                })
                .orElse(null));
        if (flagged != null) {
            log.error("Disbursement of loan {} failed after retries; manual follow-up needed", loanId);
            events.publish(Topics.LOAN_FIAT_FAILED, flagged.getUserId(),
                    LoanService.event(flagged, month, SETTLEMENT_FAILED), cause);
        }
    }

    static void syncQuietly(NessieSyncService sync, AppUser user) {
        try {
            sync.sync(user);
        } catch (RuntimeException ex) {
            log.warn("Post-settlement sync failed for user {}: {}", user.getId(), ex.getMessage());
        }
    }
}
