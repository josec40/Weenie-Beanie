package com.shbean.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.shbean.demo.DemoScenario;
import com.shbean.forecast.ForecastEngine.HistoryTxn;
import com.shbean.forecast.ForecastEngine.Input;
import com.shbean.forecast.ForecastEngine.Line;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastEngine.PlannedExpense;
import com.shbean.forecast.ForecastEngine.Result;
import com.shbean.forecast.ForecastEngine.Source;

class ForecastEngineTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    @Test
    void emptyHistoryCarriesStartingBalanceFlat() {
        Result r = run(new BigDecimal("500"), List.of(), List.of());

        assertThat(r.months()).hasSize(12);
        assertThat(r.discretionaryMonthly()).isEqualByComparingTo("0");
        assertThat(r.months()).allSatisfy(m -> {
            assertThat(m.net()).isEqualByComparingTo("0");
            assertThat(m.endingBalance()).isEqualByComparingTo("500");
            assertThat(m.shortfall()).isFalse();
            assertThat(m.lines()).isEmpty();
        });
        assertThat(r.months().getFirst().month()).isEqualTo(NOW.plusMonths(1));
    }

    @Test
    void recurringExpenseEndingMidHorizonOnlyAppliesInActiveMonths() {
        PlannedExpense carLoan = new PlannedExpense("Car loan", new BigDecimal("300"), "Loans", NOW.minusMonths(2), 5);

        Result r = run(new BigDecimal("1000"), List.of(), List.of(carLoan));

        // Started 2 months ago for 5 months (Aug-Dec) -> only months +1 and +2 remain in the horizon.
        assertThat(r.months()).extracting(m -> m.expenses().intValue())
                .containsExactly(300, 300, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertThat(r.months().get(1).endingBalance()).isEqualByComparingTo("400");
        assertThat(r.months().get(11).endingBalance()).isEqualByComparingTo("400");
        assertThat(r.months().getFirst().lines()).extracting(Line::source).containsExactly(Source.RECURRING_EXPENSE);
    }

    @Test
    void biweeklyIncomeProjectsTwoOrThreePaydaysPerMonth() {
        List<HistoryTxn> history = new ArrayList<>();
        LocalDate lastPayday = LocalDate.of(2026, 9, 25);
        for (LocalDate d = lastPayday; !d.isBefore(NOW.minusMonths(12).atDay(1)); d = d.minusDays(14)) {
            history.add(txn(d, "1000", "Income", "Payroll", "DEPOSIT:payroll"));
        }

        Result r = run(BigDecimal.ZERO, history, List.of());

        long expectedTotal = 0;
        for (Month m : r.months()) {
            long paydays = lastPayday.plusDays(14).datesUntil(m.month().atEndOfMonth().plusDays(1), java.time.Period.ofDays(14))
                    .filter(d -> YearMonth.from(d).equals(m.month())).count();
            assertThat(m.income()).isEqualByComparingTo(BigDecimal.valueOf(1000 * paydays));
            assertThat(paydays).isBetween(2L, 3L);
            expectedTotal += 1000 * paydays;
        }
        assertThat(r.months().getLast().endingBalance()).isEqualByComparingTo(BigDecimal.valueOf(expectedTotal));
        assertThat(r.months()).anySatisfy(m -> assertThat(m.income()).isEqualByComparingTo("3000"));
        assertThat(r.months().getFirst().lines()).extracting(Line::source).containsOnly(Source.BIWEEKLY);
    }

    @Test
    void endedPatternIsNotProjected() {
        List<HistoryTxn> history = new ArrayList<>();
        for (int k = 12; k >= 4; k--) {
            history.add(txn(NOW.minusMonths(k).atDay(5), "-200", "Loans", "Old loan", "PURCHASE:old-loan"));
        }

        Result r = run(new BigDecimal("100"), history, List.of());

        assertThat(r.months()).allSatisfy(m -> assertThat(m.expenses()).isEqualByComparingTo("0"));
    }

    @Test
    void userEnteredExpenseReplacesMatchingDetectedPattern() {
        List<HistoryTxn> history = new ArrayList<>();
        for (int k = 8; k >= 1; k--) {
            history.add(txn(NOW.minusMonths(k).atDay(1), "-1450", "Housing", "Rent", "PURCHASE:rent"));
        }
        PlannedExpense lease = new PlannedExpense("Lease", new BigDecimal("1450"), "Housing", NOW.plusMonths(1), 2);

        Result r = run(new BigDecimal("5000"), history, List.of(lease));

        assertThat(r.months().get(0).lines()).extracting(Line::label).containsExactly("Lease");
        assertThat(r.months().get(1).expenses()).isEqualByComparingTo("1450");
        assertThat(r.months().get(2).expenses()).isEqualByComparingTo("0");
    }

    @Test
    void unpatternedSpendingBecomesMedianDiscretionary() {
        List<HistoryTxn> history = new ArrayList<>();
        int[] oneOffs = {-120, -80, -200, -100, -90, -400};
        for (int k = 1; k <= 6; k++) {
            history.add(txn(NOW.minusMonths(k).atDay(10), String.valueOf(oneOffs[k - 1]), "Shopping", "Store " + k, "PURCHASE:store-" + k));
        }

        Result r = run(new BigDecimal("1000"), history, List.of());

        assertThat(r.discretionaryMonthly()).isEqualByComparingTo("-110");
        assertThat(r.months().getFirst().lines()).extracting(Line::source).containsExactly(Source.DISCRETIONARY);
        assertThat(r.months().getLast().endingBalance()).isEqualByComparingTo("-320");
    }

    @Test
    void demoScenarioShowsShortfallInMonthFourAndSurplusInMonthSix() {
        List<HistoryTxn> history = DemoScenario.history(NOW, 42L).stream()
                .map(t -> txn(t.date(),
                        (t.kind() == DemoScenario.Kind.DEPOSIT ? t.amount() : t.amount().negate()).toPlainString(),
                        "cat", t.description(), t.kind() + ":" + (t.merchantKey() != null ? t.merchantKey() : t.description())))
                .toList();

        Result r = run(DemoScenario.CURRENT_BALANCE, history, List.of());
        List<Month> m = r.months();

        assertThat(r.discretionaryMonthly()).isEqualByComparingTo("0");
        assertThat(m.subList(0, 3)).allSatisfy(x -> {
            assertThat(x.net()).isPositive();
            assertThat(x.shortfall()).isFalse();
        });
        assertThat(m.get(3).shortfall()).isTrue();
        assertThat(m.get(3).lines()).anySatisfy(l -> {
            assertThat(l.label()).isEqualTo("Annual auto insurance premium");
            assertThat(l.source()).isEqualTo(Source.ANNUAL);
        });
        assertThat(m.get(5).net()).isGreaterThan(new BigDecimal("1000"));
        assertThat(m.get(5).shortfall()).isFalse();
        assertThat(m.get(5).lines()).anySatisfy(l -> assertThat(l.label()).isEqualTo("Annual performance bonus"));
    }

    private static Result run(BigDecimal start, List<HistoryTxn> history, List<PlannedExpense> planned) {
        Map<YearMonth, BigDecimal> monthlyNet = history.stream().collect(Collectors.groupingBy(
                t -> YearMonth.from(t.date()), Collectors.reducing(BigDecimal.ZERO, HistoryTxn::amount, BigDecimal::add)));
        return ForecastEngine.forecast(new Input(NOW, 12, start, history, monthlyNet, planned));
    }

    private static HistoryTxn txn(LocalDate date, String amount, String category, String label, String key) {
        return new HistoryTxn(date, new BigDecimal(amount), category, label, key);
    }
}
