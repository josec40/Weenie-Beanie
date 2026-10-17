package com.shbean.demo;

import com.shbean.demo.DemoScenario.Kind;
import com.shbean.demo.DemoScenario.PlannedTxn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DemoScenarioTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);
    private final List<PlannedTxn> history = DemoScenario.history(NOW, 42L);

    @Test
    void isDeterministic() {
        assertThat(DemoScenario.history(NOW, 42L)).isEqualTo(history);
    }

    @Test
    void coversHistoryWindowAndUsesWholeDollars() {
        assertThat(history).allSatisfy(t -> {
            YearMonth m = YearMonth.from(t.date());
            assertThat(m).isBetween(NOW.minusMonths(DemoScenario.HISTORY_MONTHS), NOW.minusMonths(1));
            assertThat(t.amount().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
            assertThat(t.amount()).isPositive();
        });
    }

    @Test
    void annualItemsLandTwelveMonthsBeforeShortfallAndSurplus() {
        assertThat(monthsOf("Annual auto insurance premium"))
                .containsExactlyInAnyOrder(NOW.plusMonths(4 - 12), NOW.plusMonths(4 - 24));
        assertThat(monthsOf("Annual performance bonus"))
                .containsExactlyInAnyOrder(NOW.plusMonths(6 - 12), NOW.plusMonths(6 - 24));
    }

    @Test
    void steadyMonthsAreModestlyPositiveSoInsuranceMonthIsAShortfall() {
        Map<YearMonth, BigDecimal> net = history.stream().collect(Collectors.groupingBy(
                t -> YearMonth.from(t.date()),
                Collectors.reducing(BigDecimal.ZERO, t -> t.kind() == Kind.DEPOSIT ? t.amount() : t.amount().negate(), BigDecimal::add)));

        YearMonth insurance = NOW.plusMonths(DemoScenario.INSURANCE_MONTH_OFFSET);
        YearMonth bonus = NOW.plusMonths(DemoScenario.BONUS_MONTH_OFFSET);
        List<YearMonth> special = List.of(insurance, insurance.minusMonths(12), bonus, bonus.minusMonths(12));

        assertThat(net.get(insurance)).isLessThan(BigDecimal.valueOf(-800));
        assertThat(net.get(bonus)).isGreaterThan(BigDecimal.valueOf(1000));
        double steadyAverage = net.entrySet().stream()
                .filter(e -> !special.contains(e.getKey()))
                .mapToDouble(e -> e.getValue().doubleValue())
                .average().orElseThrow();
        assertThat(steadyAverage).isBetween(50.0, 300.0);
    }

    private List<YearMonth> monthsOf(String description) {
        return history.stream().filter(t -> t.description().equals(description)).map(t -> YearMonth.from(t.date())).toList();
    }
}
