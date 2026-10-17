package com.shbean.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
import com.shbean.forecast.ForecastEngine.HistoryTxn;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastEngine.PlannedExpense;
import com.shbean.forecast.ForecastEngine.Result;
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

    public ForecastService(TransactionStore store, RecurringExpenseRepository recurring) {
        this.store = store;
        this.recurring = recurring;
    }

    public Forecast forecast(AppUser user, int horizonMonths) {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
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
                balance.map(BalanceSnapshot::balance).orElse(BigDecimal.ZERO), history, monthlyNet, planned));
        return new Forecast(r.currentMonth(), r.startingBalance(), balance.map(BalanceSnapshot::capturedAt).orElse(null),
                r.discretionaryMonthly(), r.months());
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
