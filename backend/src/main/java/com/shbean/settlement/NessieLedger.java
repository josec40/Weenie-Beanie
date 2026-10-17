package com.shbean.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreatePurchaseRequest;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.Purchase;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.settlement.SettlementSteps.State;
import com.shbean.settlement.SettlementSteps.Step;

/**
 * Fiat saga steps. Nessie has no idempotency keys, so every transfer carries a description unique to the loan and
 * step; before creating one, the account is checked for it. A crash between Nessie's response and recording it here
 * therefore never posts the money twice.
 */
@Component
public class NessieLedger {

    public static final String BEAN_MART_MERCHANT_ID = "477c05c9-63e8-444b-b9cf-06fad9502dda";

    private final NessieClient nessie;
    private final SettlementSteps steps;

    public NessieLedger(NessieClient nessie, SettlementSteps steps) {
        this.nessie = nessie;
        this.steps = steps;
    }

    public static String disbursementDescription(UUID loanId) {
        return "ShBean micro-loan disbursement " + loanId;
    }

    public static String repaymentDescription(UUID loanId, int attempt) {
        return "ShBean micro-loan repayment " + loanId + " #" + attempt;
    }

    public static String reversalDescription(UUID loanId, int attempt) {
        return "ShBean micro-loan repayment reversal " + loanId + " #" + attempt;
    }

    public static String purchaseDescription(UUID loanId) {
        return "ShBean micro-loan purchase " + loanId;
    }

    public String resolveMerchantId(String merchantId) {
        if (merchantId != null && merchantId.length() >= 24) {
            return merchantId;
        }
        try {
            List<Merchant> merchants = nessie.listMerchants();
            if (merchants != null) {
                for (Merchant m : merchants) {
                    if ("Bean Mart".equalsIgnoreCase(m.name()) && m.id() != null && m.id().length() >= 24) {
                        return m.id();
                    }
                }
            }
        } catch (Exception ex) {
            // fallback
        }
        return BEAN_MART_MERCHANT_ID;
    }

    private List<Purchase> safeListPurchases(String accountId) {
        try {
            return nessie.listPurchases(accountId);
        } catch (Exception e) {
            return List.of();
        }
    }

    public String purchase(UUID loanId, String step, String accountId, String merchantId, LocalDate date,
                           BigDecimal amount, String description) {
        String realMerchantId = resolveMerchantId(merchantId);
        return recorded(loanId, step)
                .or(() -> reconcilePurchases(loanId, step, safeListPurchases(accountId), description))
                .orElseGet(() -> create(loanId, step, () -> nessie.createPurchase(accountId,
                        new CreatePurchaseRequest(realMerchantId, "balance", date, amount, "completed", description)).id()));
    }

    private Optional<String> reconcilePurchases(UUID loanId, String step, List<Purchase> purchases, String description) {
        Optional<String> found = purchases == null ? Optional.empty() : purchases.stream()
                .filter(p -> description.equals(p.description()))
                .map(Purchase::id)
                .findFirst();
        found.ifPresent(id -> steps.done(loanId, step, null, id));
        return found;
    }

    public String deposit(UUID loanId, String step, String accountId, LocalDate date, BigDecimal amount, String description) {
        return recorded(loanId, step)
                .or(() -> reconcile(loanId, step, nessie.listDeposits(accountId), description))
                .orElseGet(() -> create(loanId, step, () -> nessie.createDeposit(accountId,
                        new CreateTransferRequest("balance", date, "completed", amount, description)).id()));
    }

    public String withdraw(UUID loanId, String step, String accountId, LocalDate date, BigDecimal amount,
                           String description) {
        return existingWithdrawal(loanId, step, accountId, description)
                .orElseGet(() -> create(loanId, step, () -> nessie.createWithdrawal(accountId,
                        new CreateTransferRequest("balance", date, "completed", amount, description)).id()));
    }

    /** The withdrawal for this step if it was already posted (recorded here or found in Nessie). */
    public Optional<String> existingWithdrawal(UUID loanId, String step, String accountId, String description) {
        return recorded(loanId, step).or(() -> reconcile(loanId, step, nessie.listWithdrawals(accountId), description));
    }

    private Optional<String> recorded(UUID loanId, String step) {
        return steps.find(loanId, step).filter(s -> s.state() == State.DONE).map(Step::externalId);
    }

    private Optional<String> reconcile(UUID loanId, String step, List<Transfer> transfers, String description) {
        Optional<String> found = transfers == null ? Optional.empty() : transfers.stream()
                .filter(t -> description.equals(t.description()))
                .map(Transfer::id)
                .findFirst();
        found.ifPresent(id -> steps.done(loanId, step, null, id));
        return found;
    }

    private String create(UUID loanId, String step, Supplier<String> post) {
        String id = post.get();
        steps.done(loanId, step, null, id);
        return id;
    }
}
