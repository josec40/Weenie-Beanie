package com.shbean.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loan_offer")
public class LoanOffer {

    public enum Status { OFFERED, ACCEPTED, DECLINED, SUPERSEDED, EXPIRED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "forecast_snapshot_id")
    private UUID forecastSnapshotId;

    @Column(name = "shortfall_month", nullable = false)
    private LocalDate shortfallMonth;

    @Column(name = "repayment_month", nullable = false)
    private LocalDate repaymentMonth;

    @Column(nullable = false)
    private BigDecimal principal;

    @Column(nullable = false)
    private BigDecimal fee;

    private BigDecimal deficit;

    @Column(name = "projected_surplus")
    private BigDecimal projectedSurplus;

    private Short tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(nullable = false)
    private String purpose = "BASELINE";

    @Column(name = "purchase_label")
    private String purchaseLabel;

    @Column(name = "purchase_amount")
    private BigDecimal purchaseAmount;

    protected LoanOffer() {
    }

    public LoanOffer(UUID userId, UUID forecastSnapshotId, OfferEngine.Proposal p, String explanation) {
        this(userId, forecastSnapshotId, p, explanation, "BASELINE", null, null);
    }

    public LoanOffer(UUID userId, UUID forecastSnapshotId, OfferEngine.Proposal p, String explanation,
                     String purpose, String purchaseLabel, BigDecimal purchaseAmount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.forecastSnapshotId = forecastSnapshotId;
        this.shortfallMonth = p.shortfallMonth().atDay(1);
        this.repaymentMonth = p.repaymentMonth().atDay(1);
        this.principal = p.principal();
        this.fee = p.fee();
        this.deficit = p.deficit();
        this.projectedSurplus = p.projectedSurplus();
        this.tier = (short) p.tier();
        this.status = Status.OFFERED;
        this.explanation = explanation;
        this.purpose = purpose != null ? purpose : "BASELINE";
        this.purchaseLabel = purchaseLabel;
        this.purchaseAmount = purchaseAmount;
        this.createdAt = Instant.now();
    }

    /** Same terms as a fresh proposal (so a recomputed forecast does not churn offers). */
    public boolean sameTerms(OfferEngine.Proposal p) {
        return getShortfallMonth().equals(p.shortfallMonth()) && getRepaymentMonth().equals(p.repaymentMonth())
                && principal.compareTo(p.principal()) == 0 && fee.compareTo(p.fee()) == 0;
    }

    public void close(Status newStatus) {
        this.status = newStatus;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public YearMonth getShortfallMonth() {
        return YearMonth.from(shortfallMonth);
    }

    public YearMonth getRepaymentMonth() {
        return YearMonth.from(repaymentMonth);
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getDeficit() {
        return deficit;
    }

    public BigDecimal getProjectedSurplus() {
        return projectedSurplus;
    }

    public Status getStatus() {
        return status;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getPurchaseLabel() {
        return purchaseLabel;
    }

    public BigDecimal getPurchaseAmount() {
        return purchaseAmount;
    }
}
