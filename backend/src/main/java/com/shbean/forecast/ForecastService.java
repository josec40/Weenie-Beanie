package com.shbean.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shbean.cashflow.RecurringExpenseRepository;
import com.shbean.cashflow.TransactionStore;
import com.shbean.cashflow.TransactionStore.BalanceSnapshot;
import com.shbean.cashflow.TransactionStore.MonthlyCashflow;
import com.shbean.cashflow.TransactionStore.TransactionRow;
import com.shbean.demo.DemoClock;
import com.shbean.forecast.ForecastEngine.HistoryTxn;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastEngine.OneOff;
import com.shbean.forecast.ForecastEngine.PlannedExpense;
import com.shbean.forecast.ForecastEngine.Result;
import com.shbean.forecast.ForecastEngine.Source;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;
import com.shbean.user.AppUser;

@Service
public class ForecastService {

    /** {@code balanceAsOf} is null when the user has no Nessie account or no sync yet. */
    public record Forecast(YearMonth currentMonth, BigDecimal startingBalance, Instant balanceAsOf,
                           BigDecimal discretionaryMonthly, List<Month> months) {
    }

    public static final int MAX_HORIZON = 24;

    private final TransactionStore store;
    private final RecurringExpenseRepository recurring;
    private final LoanRepository loans;
    private final DemoClock clock;

    public ForecastService(TransactionStore store, RecurringExpenseRepository recurring, LoanRepository loans,
                           DemoClock clock) {
        this.store = store;
        this.recurring = recurring;
        this.loans = loans;
        this.clock = clock;
    }

    public Forecast forecast(AppUser user, int horizonMonths) {
        YearMonth current = clock.currentMonth();
        int horizon = Math.clamp(horizonMonths, 1, MAX_HORIZON);

        List<HistoryTxn> history = store.transactions(user.getId(),
                        current.minusMonths(ForecastEngine.LOOKBACK_MONTHS).atDay(1), current.atDay(1))
                .stream().map(ForecastService::toHistory).toList();
        Map<YearMonth, BigDecimal> monthlyNet = store.monthlyCashflow(user.getId()).stream()
                .collect(Collectors.groupingBy(c -> YearMonth.from(c.month()),
                        Collectors.reducing(BigDecimal.ZERO, MonthlyCashflow::net, BigDecimal::add)));
        List<PlannedExpense> planned = recurring.findByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(e -> new PlannedExpense(e.getName(), e.getAmount(), e.getCategory(), e.getStartMonth(), e.getDurationMonths()))
                .toList();
        Optional<BalanceSnapshot> balance = Optional.ofNullable(user.getNessieCheckingAccountId())
                .flatMap(store::latestBalance);

        Result r = ForecastEngine.forecast(new ForecastEngine.Input(current, horizon,
                balance.map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO), history, monthlyNet, planned,
                loanFlows(user, current)));
        return new Forecast(r.currentMonth(), r.startingBalance(), balance.map(BalanceSnapshot::capturedAt).orElse(null),
                r.discretionaryMonthly(), r.months());
    }

    /** Scheduled money movements of the user's open loan that have not settled yet. */
    private List<OneOff> loanFlows(AppUser user, YearMonth current) {
        List<OneOff> flows = new ArrayList<>();
        loans.findFirstByUserIdAndStatusIn(user.getId(), Loan.OPEN).ifPresent(l -> {
            boolean notDisbursed = l.getStatus() == Loan.Status.ACCEPTED || l.getStatus() == Loan.Status.DISBURSING;
            if (notDisbursed && l.getDisburseMonth().isAfter(current)) {
                flows.add(new OneOff(l.getDisburseMonth(), "Micro-loan disbursement", "Loan", l.getPrincipal(), Source.LOAN));
            }
            if (l.getStatus() != Loan.Status.REPAYING) {
                YearMonth repay = l.getStatus() == Loan.Status.LATE ? current.plusMonths(1) : l.getRepayMonth();
                if (repay.isAfter(current)) {
                    flows.add(new OneOff(repay, "Micro-loan repayment", "Loan", l.getTotalDue().negate(), Source.LOAN));
                }
            }
        });
        return flows;
    }

    private static HistoryTxn toHistory(TransactionRow t) {
        String payee = t.merchantId() != null ? t.merchantId() : normalize(t.description());
        String label = t.description() == null || t.description().isBlank() ? t.category() : t.description();
        return new HistoryTxn(t.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate(), t.amount(), t.category(), label,
                t.kind() + ":" + payee);
    }

    private static String normalize(String description) {
        return description == null ? "" : description.toLowerCase(Locale.ROOT).replaceAll("[0-9#]", "").strip();
    }
}
