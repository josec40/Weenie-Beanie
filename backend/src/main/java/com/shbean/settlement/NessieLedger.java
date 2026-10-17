package com.shbean.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
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
