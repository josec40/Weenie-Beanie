package com.shbean.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Events.TransactionsIngested;
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
    static final String PURCHASE = "PURCHASE_FIAT";
    public static final String SETTLEMENT_FAILED = "SETTLEMENT_FAILED";

    private static final Logger log = LoggerFactory.getLogger(DisbursementSaga.class);

    private final LoanRepository loans;
    private final AppUserRepository users;
    private final ObjectProvider<LoanLedger> chain;
    private final NessieLedger fiat;
    private final NessieSyncService sync;
    private final TransactionStore store;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    public DisbursementSaga(LoanRepository loans, AppUserRepository users, ObjectProvider<LoanLedger> chain,
                            NessieLedger fiat, NessieSyncService sync, TransactionStore store,
                            EventPublisher events, TransactionTemplate tx) {
        this.loans = loans;
        this.users = users;
        this.chain = chain;
        this.fiat = fiat;
        this.sync = sync;
        this.store = store;
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

        String vendorSig = null;
        if ("PURCHASE".equals(loan.getPurpose()) && loan.getPurchaseAmount() != null) {
            if (ledger != null) {
                vendorSig = ledger.payVendor(loan);
            }
            if (loan.getPayeeMerchantId() != null) {
                fiat.purchase(loanId, PURCHASE, user.getNessieCheckingAccountId(), loan.getPayeeMerchantId(),
                        month.atDay(1), loan.getPurchaseAmount(), NessieLedger.purchaseDescription(loanId));
            }
        }

        String disburseSig = signature;
        String finalVendorSig = vendorSig;
        Loan done = tx.execute(s -> loans.findById(loanId)
                .filter(l -> l.getStatus() == Loan.Status.DISBURSING)
                .map(l -> {
                    l.disbursed(depositId, disburseSig, finalVendorSig);
                    return l;
                })
                .orElse(null));
        if (done == null) {
            return;
        }

        // Direct ledger updates: record deposit and purchase in account_transaction and update balance_snapshot
        List<TransactionRow> newRows = new ArrayList<>();
        String depTxnId = depositId != null ? depositId : "loan-deposit-" + loanId;
        newRows.add(new TransactionRow(
                depTxnId,
                Instant.now(),
                user.getId(),
                user.getNessieCheckingAccountId(),
                "DEPOSIT",
                done.getPrincipal(),
                "Income",
                "ShBean micro-loan disbursement " + loanId,
                null,
                "completed"
        ));

        if ("PURCHASE".equals(done.getPurpose()) && done.getPurchaseAmount() != null) {
            String purchTxnId = "purchase-" + loanId;
            newRows.add(new TransactionRow(
                    purchTxnId,
                    Instant.now(),
                    user.getId(),
                    user.getNessieCheckingAccountId(),
                    "PURCHASE",
                    done.getPurchaseAmount().abs().negate(),
                    "Shopping",
                    "Bean Mart - " + (done.getPurchaseLabel() != null ? done.getPurchaseLabel() : "Store purchase"),
                    done.getPayeeMerchantId(),
                    "completed"
            ));
        }

        store.insertNew(newRows);
        BigDecimal currentBal = store.latestBalance(user.getNessieCheckingAccountId())
                .map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO);
        BigDecimal newBal;
        if ("PURCHASE".equals(done.getPurpose()) && done.getPurchaseAmount() != null) {
            newBal = currentBal.add(done.getPrincipal()).subtract(done.getPurchaseAmount()).max(BigDecimal.ZERO);
        } else {
            newBal = currentBal.add(done.getPrincipal());
        }
        store.insertBalanceSnapshot(user.getNessieCheckingAccountId(), Instant.now(), user.getId(), newBal);
        events.publish(Topics.TRANSACTIONS_INGESTED, user.getId(),
                new TransactionsIngested(newRows.size(), LocalDate.now(), LocalDate.now(), newBal));
        events.publish(Topics.CASHFLOW_CHANGED, user.getId(), new CashflowChanged(CashflowReason.TRANSACTIONS));

        syncQuietly(sync, user);
        String payee = done.getPurchaseLabel();
        LoanEvent event = LoanService.event(done, month, null, payee);
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
