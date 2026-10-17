package com.shbean.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shbean.ai.ExplanationService;
import com.shbean.ai.ExplanationService.Explanation;
import com.shbean.ai.ExplanationService.PurchaseFacts;
import com.shbean.cashflow.NessieSyncService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.cashflow.TransactionStore.BalanceSnapshot;
import com.shbean.cashflow.TransactionStore.TransactionRow;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Events.TransactionsIngested;
import com.shbean.events.Topics;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastService;
import com.shbean.forecast.ForecastService.Forecast;
import com.shbean.loan.CreditTiers;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanProperties;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.OfferEngine;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreatePurchaseRequest;
import com.shbean.settlement.NessieLedger;
import com.shbean.user.AppUser;

@Service
public class PurchaseService {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("50000");

    private final ForecastService forecasts;
    private final ExplanationService explanations;
    private final LoanRepository loans;
    private final CreditTiers tiers;
    private final LoanProperties props;
    private final TransactionStore store;
    private final NessieClient nessie;
    private final NessieSyncService sync;
    private final EventPublisher events;

    public PurchaseService(ForecastService forecasts, ExplanationService explanations, LoanRepository loans,
                           CreditTiers tiers, LoanProperties props, TransactionStore store, NessieClient nessie,
                           NessieSyncService sync, EventPublisher events) {
        this.forecasts = forecasts;
        this.explanations = explanations;
        this.loans = loans;
        this.tiers = tiers;
        this.props = props;
        this.store = store;
        this.nessie = nessie;
        this.sync = sync;
        this.events = events;
    }

    public PurchaseCheckResult purchaseCheck(AppUser user, BigDecimal amount, String label) {
        validateAmount(amount);
        String cleanLabel = sanitizeLabel(label);

        Optional<BalanceSnapshot> balance = Optional.ofNullable(user.getNessieCheckingAccountId())
                .flatMap(store::latestBalance);
        BigDecimal balanceNow = balance.map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO);
        BigDecimal gapToday = amount.subtract(balanceNow).max(BigDecimal.ZERO);

        Forecast whatIf = forecasts.forecast(user, 12, amount);
        Forecast baseline = forecasts.forecast(user, 12, BigDecimal.ZERO);

        String affordableFrom = computeAffordableFrom(baseline.months(), amount, props.minBufferAfterRepay());
        String laterShortfallMonth = computeLaterShortfallMonth(whatIf.months(), props.repaymentWindowMonths());

        if (loans.findFirstByUserIdAndStatusIn(user.getId(), Loan.OPEN).isPresent()) {
            PurchaseFacts facts = new PurchaseFacts(amount, PurchaseVerdict.NOT_ELIGIBLE.name(), balanceNow, gapToday,
                    null, null, null, null, affordableFrom, laterShortfallMonth);
            Explanation explanation = explanations.explainPurchase(facts);
            return new PurchaseCheckResult(PurchaseVerdict.NOT_ELIGIBLE, "HAS_OPEN_LOAN", balanceNow, gapToday,
                    null, affordableFrom, laterShortfallMonth, whatIf.months(), explanation);
        }

        int tier = tiers.tierOf(user.getId());
        BigDecimal startAfterPurchase = balanceNow.subtract(amount);
        Decision decision = OfferEngine.decidePurchase(startAfterPurchase, whatIf.months(), tier, props);

        PurchaseVerdict verdict;
        String reason = null;
        PurchaseProposal proposal = null;

        if (decision.reason() == OfferEngine.NoOfferReason.NO_SHORTFALL) {
            verdict = PurchaseVerdict.AFFORDABLE;
        } else if (decision.proposal().isPresent()) {
            verdict = PurchaseVerdict.LOAN_AVAILABLE;
            OfferEngine.Proposal p = decision.proposal().get();
            BigDecimal totalDue = p.principal().add(p.fee());
            proposal = new PurchaseProposal(p.principal(), p.fee(), p.repaymentMonth().toString(), totalDue);
        } else {
            verdict = PurchaseVerdict.NOT_ELIGIBLE;
            reason = decision.reason() != null ? decision.reason().name() : "NOT_ELIGIBLE";
        }

        PurchaseFacts facts = new PurchaseFacts(
                amount,
                verdict.name(),
                balanceNow,
                gapToday,
                proposal != null ? proposal.principal() : null,
                proposal != null ? proposal.fee() : null,
                proposal != null ? proposal.repaymentMonth() : null,
                proposal != null ? proposal.totalDue() : null,
                affordableFrom,
                laterShortfallMonth
        );
        Explanation explanation = explanations.explainPurchase(facts);

        return new PurchaseCheckResult(verdict, reason, balanceNow, gapToday, proposal, affordableFrom,
                laterShortfallMonth, whatIf.months(), explanation);
    }

    public boolean payMerchant(AppUser user, String merchantId, BigDecimal amount, String description) {
        if (user.getNessieCheckingAccountId() == null) {
            throw new IllegalArgumentException("No linked checking account");
        }
        validateAmount(amount);
        String cleanDesc = sanitizeLabel(description);
        if (cleanDesc == null || cleanDesc.isBlank()) {
            cleanDesc = "Store purchase";
        }
        String realMerchantId = (merchantId != null && merchantId.length() >= 24)
                ? merchantId : NessieLedger.BEAN_MART_MERCHANT_ID;
        try {
            nessie.createPurchase(user.getNessieCheckingAccountId(),
                    new CreatePurchaseRequest(realMerchantId, "balance", LocalDate.now(), amount, "completed", cleanDesc));
        } catch (RuntimeException ex) {
            // best effort sync in sandbox
        }

        // Direct ledger updates: record purchase transaction and update balance_snapshot
        String txnId = "purchase-" + UUID.randomUUID();
        TransactionRow txn = new TransactionRow(
                txnId,
                Instant.now(),
                user.getId(),
                user.getNessieCheckingAccountId(),
                "PURCHASE",
                amount.abs().negate(),
                "Shopping",
                "Bean Mart - " + cleanDesc,
                realMerchantId,
                "completed"
        );
        store.insertNew(List.of(txn));
        BigDecimal prevBal = store.latestBalance(user.getNessieCheckingAccountId())
                .map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO);
        BigDecimal newBal = prevBal.subtract(amount).max(BigDecimal.ZERO);
        store.insertBalanceSnapshot(user.getNessieCheckingAccountId(), Instant.now(), user.getId(), newBal);
        events.publish(Topics.TRANSACTIONS_INGESTED, user.getId(),
                new TransactionsIngested(1, LocalDate.now(), LocalDate.now(), newBal));
        events.publish(Topics.CASHFLOW_CHANGED, user.getId(), new CashflowChanged(CashflowReason.TRANSACTIONS));

        try {
            sync.sync(user);
        } catch (RuntimeException ex) {
            // best effort sync
        }
        return true;
    }

    public static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount exceeds maximum allowed limit of " + MAX_AMOUNT);
        }
    }

    public static String sanitizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String stripped = label.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return stripped.length() > 80 ? stripped.substring(0, 80) : stripped;
    }

    private static String computeAffordableFrom(List<Month> months, BigDecimal amount, BigDecimal buffer) {
        for (int i = 0; i < months.size(); i++) {
            boolean allStayAbove = true;
            for (int k = i; k < months.size(); k++) {
                if (months.get(k).endingBalance().subtract(amount).compareTo(buffer) < 0) {
                    allStayAbove = false;
                    break;
                }
            }
            if (allStayAbove) {
                return months.get(i).month().toString();
            }
        }
        return null;
    }

    private static String computeLaterShortfallMonth(List<Month> months, int repaymentWindowMonths) {
        return months.stream()
                .skip(repaymentWindowMonths)
                .filter(Month::shortfall)
                .findFirst()
                .map(m -> m.month().toString())
                .orElse(null);
    }
}
