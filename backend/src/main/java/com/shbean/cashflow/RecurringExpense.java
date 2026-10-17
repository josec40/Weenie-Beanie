package com.shbean.cashflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    @Column(name = "day_of_month", nullable = false)
    private short dayOfMonth;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "nessie_bill_id")
    private String nessieBillId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurringExpense() {
    }

    public RecurringExpense(UUID userId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public void update(String name, BigDecimal amount, String category, int dayOfMonth, YearMonth startMonth,
                       Integer durationMonths) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.dayOfMonth = (short) dayOfMonth;
        this.startMonth = startMonth.atDay(1);
        this.durationMonths = durationMonths;
    }

    /** True if a payment falls in {@code month}. */
    public boolean activeIn(YearMonth month) {
        YearMonth start = getStartMonth();
        return !month.isBefore(start) && (durationMonths == null || month.isBefore(start.plusMonths(durationMonths)));
    }

    /** Last month with a payment, or null if open-ended. */
    public YearMonth getEndMonth() {
        return durationMonths == null ? null : getStartMonth().plusMonths(durationMonths - 1L);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public YearMonth getStartMonth() {
        return YearMonth.from(startMonth);
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public String getNessieBillId() {
        return nessieBillId;
    }

    public void setNessieBillId(String nessieBillId) {
        this.nessieBillId = nessieBillId;
    }
}
