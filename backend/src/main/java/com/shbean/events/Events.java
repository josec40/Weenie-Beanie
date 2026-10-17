package com.shbean.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Event payloads (serialized into {@link EventEnvelope#payload()}). Never put PII or Nessie/account ids here. */
public final class Events {

    private Events() {
    }

    public record TransactionsIngested(int count, LocalDate firstDate, LocalDate lastDate, BigDecimal balance) {
    }

    public enum CashflowReason { TRANSACTIONS, RECURRING_EXPENSE, CLOCK }

    public record CashflowChanged(CashflowReason reason) {
    }

    public record ForecastUpdated(UUID snapshotId, String currentMonth, BigDecimal startingBalance,
                                  List<String> shortfallMonths, BigDecimal lowestBalance) {
    }

    public record OfferGenerated(UUID offerId, String shortfallMonth, BigDecimal principal, BigDecimal fee,
                                 String repaymentMonth) {
    }

    /** Used for every loan.* topic; {@code month} is the business month of the step, {@code reason} set on failures. */
    public record LoanEvent(UUID loanId, String status, BigDecimal principal, BigDecimal fee, String month,
                            String reason, String payee) {
        public LoanEvent(UUID loanId, String status, BigDecimal principal, BigDecimal fee, String month, String reason) {
            this(loanId, status, principal, fee, month, reason, null);
        }
    }

    public enum RepaymentOutcome { ON_TIME, LATE, DEFAULT }

    /** {@code amount} is what was repaid (zero for defaults). */
    public record CredentialUpdateRequested(UUID loanId, RepaymentOutcome outcome, BigDecimal amount, String month) {
    }

    public record CredentialUpdated(int tier, int previousTier, int onTimeRepayments, int previousOnTimeRepayments,
                                    int lateRepayments, int defaults, boolean credentialMinted) {
    }
}
