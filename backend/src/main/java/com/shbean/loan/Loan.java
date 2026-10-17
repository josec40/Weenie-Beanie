package com.shbean.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Lifecycle: ACCEPTED -> DISBURSING -> ACTIVE -> REPAYING -> REPAID, with REPAYING -> LATE -> (REPAYING | DEFAULTED).
 * Transitions are guarded here; illegal ones throw {@link IllegalStateException}.
 */
@Entity
@Table(name = "loan")
public class Loan {

    public enum Status { ACCEPTED, DISBURSING, ACTIVE, REPAYING, LATE, REPAID, DEFAULTED }

    public static final Set<Status> OPEN = Set.of(Status.ACCEPTED, Status.DISBURSING, Status.ACTIVE, Status.REPAYING, Status.LATE);

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private UUID offerId;

    @Column(nullable = false)
    private BigDecimal principal;

    @Column(nullable = false)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "disburse_month", nullable = false)
    private LocalDate disburseMonth;

    @Column(name = "repay_month", nullable = false)
    private LocalDate repayMonth;

    @Column(name = "original_repay_month", nullable = false, updatable = false)
    private LocalDate originalRepayMonth;

    @Column(name = "grace_until_month")
    private LocalDate graceUntilMonth;

    @Column(name = "reschedule_count", nullable = false)
    private short rescheduleCount;

    @Column(name = "solana_disburse_sig")
    private String solanaDisburseSig;

    @Column(name = "solana_repay_sig")
    private String solanaRepaySig;

    @Column(name = "nessie_deposit_id")
    private String nessieDepositId;

    @Column(name = "nessie_withdrawal_id")
    private String nessieWithdrawalId;

    @Column(name = "last_failure")
    private String lastFailure;

    @Column(name = "repay_attempt", nullable = false)
    private short repayAttempt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "repaid_at")
    private Instant repaidAt;

    protected Loan() {
    }

    public Loan(LoanOffer offer) {
        this.id = UUID.randomUUID();
        this.userId = offer.getUserId();
        this.offerId = offer.getId();
        this.principal = offer.getPrincipal();
        this.fee = offer.getFee();
        this.disburseMonth = offer.getShortfallMonth().atDay(1);
        this.repayMonth = offer.getRepaymentMonth().atDay(1);
        this.originalRepayMonth = repayMonth;
        this.status = Status.ACCEPTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void startDisbursing() {
        transition(Status.ACCEPTED, Status.DISBURSING);
    }

    public void disbursed(String nessieDepositId) {
        disbursed(nessieDepositId, null);
    }

    public void disbursed(String nessieDepositId, String solanaSignature) {
        transition(Status.DISBURSING, Status.ACTIVE);
        this.nessieDepositId = nessieDepositId;
        this.solanaDisburseSig = solanaSignature;
        this.lastFailure = null;
    }

    public void startRepaying() {
        if (status != Status.ACTIVE && status != Status.LATE) {
            throw new IllegalStateException("Cannot start repayment from " + status);
        }
        status = Status.REPAYING;
        repayAttempt++;
        touch();
    }

    public void repaid(String nessieWithdrawalId) {
        repaid(nessieWithdrawalId, null);
    }

    public void repaid(String nessieWithdrawalId, String solanaSignature) {
        transition(Status.REPAYING, Status.REPAID);
        this.nessieWithdrawalId = nessieWithdrawalId;
        this.solanaRepaySig = solanaSignature;
        this.repaidAt = Instant.now();
        this.lastFailure = null;
    }

    /** Records a settlement problem that needs attention without changing the lifecycle state. */
    public void settlementProblem(String reason) {
        this.lastFailure = reason;
        touch();
    }

    /** Repayment failed a safeguard; the grace window starts at the first failure and is not extended by retries. */
    public void late(String reason, YearMonth graceUntil) {
        transition(Status.REPAYING, Status.LATE);
        this.lastFailure = reason;
        if (graceUntilMonth == null) {
            this.graceUntilMonth = graceUntil.atDay(1);
        }
    }

    public void defaulted() {
        transition(Status.LATE, Status.DEFAULTED);
    }

    /** Moves the repayment month one month later; allowed once, before repayment starts, within the window. */
    public void reschedule(YearMonth latestAllowed) {
        if (status != Status.ACCEPTED && status != Status.DISBURSING && status != Status.ACTIVE) {
            throw new IllegalArgumentException("Only loans that have not started repayment can be rescheduled");
        }
        if (rescheduleCount >= 1) {
            throw new IllegalArgumentException("A loan can be rescheduled only once");
        }
        YearMonth next = getRepayMonth().plusMonths(1);
        if (next.isAfter(latestAllowed)) {
            throw new IllegalArgumentException("Repayment cannot move past " + latestAllowed);
        }
        this.repayMonth = next.atDay(1);
        this.rescheduleCount++;
        touch();
    }

    /** Repay in {@code month} instead of the scheduled month (early repayment). */
    public void repayIn(YearMonth month) {
        if (status != Status.ACTIVE) {
            throw new IllegalArgumentException("Only active (disbursed) loans can be repaid early");
        }
        this.repayMonth = month.atDay(1);
        touch();
    }

    private void transition(Status from, Status to) {
        if (status != from) {
            throw new IllegalStateException("Cannot move loan from " + status + " to " + to);
        }
        status = to;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public BigDecimal getTotalDue() {
        return principal.add(fee);
    }

    /** Repaid in or before the month agreed at acceptance, without ever going LATE; a reschedule makes it late. */
    public boolean isOnTime() {
        return status == Status.REPAID && graceUntilMonth == null && !repayMonth.isAfter(originalRepayMonth);
    }

    public YearMonth getOriginalRepayMonth() {
        return YearMonth.from(originalRepayMonth);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOfferId() {
        return offerId;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public Status getStatus() {
        return status;
    }

    public YearMonth getDisburseMonth() {
        return YearMonth.from(disburseMonth);
    }

    public YearMonth getRepayMonth() {
        return YearMonth.from(repayMonth);
    }

    public YearMonth getGraceUntilMonth() {
        return graceUntilMonth == null ? null : YearMonth.from(graceUntilMonth);
    }

    public int getRescheduleCount() {
        return rescheduleCount;
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public String getNessieDepositId() {
        return nessieDepositId;
    }

    public String getNessieWithdrawalId() {
        return nessieWithdrawalId;
    }

    public String getSolanaDisburseSignature() {
        return solanaDisburseSig;
    }

    public String getSolanaRepaySignature() {
        return solanaRepaySig;
    }

    public int getRepayAttempt() {
        return repayAttempt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
