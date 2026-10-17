package com.shbean.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.cashflow.TransactionStore.BalanceSnapshot;
import com.shbean.cashflow.TransactionStore.TransactionRow;
import com.shbean.events.EventEnvelope;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Events.CredentialUpdateRequested;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.RepaymentOutcome;
import com.shbean.events.Events.TransactionsIngested;
import com.shbean.events.Topics;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanProperties;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/**
 * Repayment: safeguard → Nessie withdrawal → [on-ramp mint → on-chain repay] → REPAID. If the on-chain part cannot
 * complete, {@link #compensate} refunds the withdrawal and the loan goes LATE (retried next month within grace).
 * Each repayment attempt has its own steps and Nessie descriptions, so a compensated attempt is never reused.
 */
@Component
public class RepaymentSaga {

    public static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

    static String withdrawStep(int attempt) {
        return "WITHDRAW_FIAT#" + attempt;
    }

    static String refundStep(int attempt) {
        return "REFUND_FIAT#" + attempt;
    }

    private final LoanRepository loans;
    private final AppUserRepository users;
    private final ObjectProvider<LoanLedger> chain;
    private final NessieLedger fiat;
    private final TransactionStore store;
    private final NessieSyncService sync;
    private final LoanProperties props;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    public RepaymentSaga(LoanRepository loans, AppUserRepository users, ObjectProvider<LoanLedger> chain,
                         NessieLedger fiat, TransactionStore store, NessieSyncService sync, LoanProperties props,
                         EventPublisher events, TransactionTemplate tx) {
        this.loans = loans;
        this.users = users;
        this.chain = chain;
        this.fiat = fiat;
        this.store = store;
        this.sync = sync;
        this.props = props;
        this.events = events;
        this.tx = tx;
    }

    public void run(UUID loanId, YearMonth month, EventEnvelope cause) {
        Optional<Loan> pending = repaying(loanId);
        if (pending.isEmpty()) {
            return;
        }
        Loan loan = pending.get();
        AppUser user = users.findById(loan.getUserId()).orElseThrow();
        String account = user.getNessieCheckingAccountId();
        int attempt = loan.getRepayAttempt();
        String description = NessieLedger.repaymentDescription(loanId, attempt);

        Optional<String> withdrawal = fiat.existingWithdrawal(loanId, withdrawStep(attempt), account, description);
        if (withdrawal.isEmpty()) {
            // Safeguard: never auto-draft into (or near) an overdraft. Skipped on resume once the money has moved.
            BigDecimal balance = store.latestBalance(account).map(TransactionStore.BalanceSnapshot::balance)
                    .orElse(BigDecimal.ZERO);
            if (balance.subtract(loan.getTotalDue()).compareTo(props.minBufferAfterRepay()) < 0) {
                late(loanId, INSUFFICIENT_FUNDS, month, cause);
                return;
            }
            withdrawal = Optional.of(fiat.withdraw(loanId, withdrawStep(attempt), account, month.atEndOfMonth(),
                    loan.getTotalDue(), description));
        }

        LoanLedger ledger = chain.getIfAvailable();
        String signature = null;
        if (ledger != null) {
            ledger.onRamp(loan);
            signature = ledger.repay(loan);
        }
        complete(loanId, user, withdrawal.get(), signature, month, cause);
    }

    /**
     * Retries are exhausted. If the on-chain repayment landed after all, finish; otherwise refund the withdrawal
     * (if one was posted) and mark the loan LATE.
     */
    public void compensate(UUID loanId, YearMonth month, EventEnvelope cause) {
        Optional<Loan> pending = repaying(loanId);
        if (pending.isEmpty()) {
            return;
        }
        Loan loan = pending.get();
        AppUser user = users.findById(loan.getUserId()).orElseThrow();
        String account = user.getNessieCheckingAccountId();
        int attempt = loan.getRepayAttempt();
        Optional<String> withdrawal = fiat.existingWithdrawal(loanId, withdrawStep(attempt), account,
                NessieLedger.repaymentDescription(loanId, attempt));

        LoanLedger ledger = chain.getIfAvailable();
        if (ledger != null) {
            ledger.awaitRepay(loan);
            if (withdrawal.isPresent() && ledger.isRepaid(loan)) {
                complete(loanId, user, withdrawal.get(), null, month, cause);
                return;
            }
        }
        if (withdrawal.isPresent()) {
            fiat.deposit(loanId, refundStep(attempt), account, month.atEndOfMonth(), loan.getTotalDue(),
                    NessieLedger.reversalDescription(loanId, attempt));
            DisbursementSaga.syncQuietly(sync, user);
        }
        late(loanId, DisbursementSaga.SETTLEMENT_FAILED, month, cause);
    }

    private Optional<Loan> repaying(UUID loanId) {
        return loans.findById(loanId).filter(l -> l.getStatus() == Loan.Status.REPAYING);
    }

    private void complete(UUID loanId, AppUser user, String withdrawalId, String signature, YearMonth month,
                          EventEnvelope cause) {
        Loan done = tx.execute(s -> repaying(loanId).map(l -> {
            l.repaid(withdrawalId, signature);
            return l;
        }).orElse(null));
        if (done == null) {
            return;
        }

        // Direct ledger updates: record repayment withdrawal in account_transaction and update balance_snapshot
        String txnId = withdrawalId != null ? withdrawalId : "loan-repay-" + done.getId() + "-" + done.getRepayAttempt();
        TransactionRow row = new TransactionRow(
                txnId,
                Instant.now(),
                user.getId(),
                user.getNessieCheckingAccountId(),
                "WITHDRAWAL",
                done.getTotalDue().abs().negate(),
                "Loans",
                "ShBean micro-loan repayment " + done.getId(),
                null,
                "completed"
        );
        store.insertNew(List.of(row));
        BigDecimal prevBal = store.latestBalance(user.getNessieCheckingAccountId())
                .map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO);
        BigDecimal newBal = prevBal.subtract(done.getTotalDue()).max(BigDecimal.ZERO);
        store.insertBalanceSnapshot(user.getNessieCheckingAccountId(), Instant.now(), user.getId(), newBal);
        events.publish(Topics.TRANSACTIONS_INGESTED, user.getId(),
                new TransactionsIngested(1, LocalDate.now(), LocalDate.now(), newBal));
        events.publish(Topics.CASHFLOW_CHANGED, user.getId(), new CashflowChanged(CashflowReason.TRANSACTIONS));

        DisbursementSaga.syncQuietly(sync, user);
        LoanEvent event = LoanService.event(done, month, null);
        events.publish(Topics.LOAN_FIAT_SETTLED, user.getId(), event, cause);
        events.publish(Topics.LOAN_REPAID, user.getId(), event, cause);
        events.publish(Topics.CREDENTIAL_UPDATE_REQUESTED, user.getId(), new CredentialUpdateRequested(loanId,
                done.isOnTime() ? RepaymentOutcome.ON_TIME : RepaymentOutcome.LATE, done.getTotalDue(), month.toString()),
                cause);
    }

    private void late(UUID loanId, String reason, YearMonth month, EventEnvelope cause) {
        Loan late = tx.execute(s -> repaying(loanId).map(l -> {
            l.late(reason, month.plusMonths(props.graceMonths()));
            return l;
        }).orElse(null));
        if (late != null) {
            events.publish(Topics.LOAN_FIAT_FAILED, late.getUserId(), LoanService.event(late, month, reason), cause);
        }
    }
}
