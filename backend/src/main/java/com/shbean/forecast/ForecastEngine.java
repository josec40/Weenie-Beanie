package com.shbean.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic 12-month cash-flow projection (no AI, no I/O).
 * <ul>
 *   <li>Recurring patterns (weekly, biweekly, monthly, annual) are detected per merchant/description from history.</li>
 *   <li>Everything else is "everyday spending": the median of (monthly net from the CAGG minus patterned items)
 *       over the last 6 complete months.</li>
 *   <li>User-entered recurring expenses apply only in their active months and replace a matching detected pattern.</li>
 * </ul>
 * Month 1 is the month after {@code currentMonth}; the remainder of the current month is not modeled.
 */
public final class ForecastEngine {

    public enum Source { MONTHLY, BIWEEKLY, WEEKLY, ANNUAL, RECURRING_EXPENSE, DISCRETIONARY }

    /** {@code amount} is signed: inflow &gt; 0, outflow &lt; 0. {@code key} groups the same payee/payer. */
    public record HistoryTxn(LocalDate date, BigDecimal amount, String category, String label, String key) {
    }

    public record PlannedExpense(String name, BigDecimal amount, String category, YearMonth start, Integer durationMonths) {
        boolean activeIn(YearMonth m) {
            return !m.isBefore(start) && (durationMonths == null || m.isBefore(start.plusMonths(durationMonths)));
        }
    }

    public record Input(YearMonth currentMonth, int horizonMonths, BigDecimal startingBalance, List<HistoryTxn> history,
                        Map<YearMonth, BigDecimal> monthlyNet, List<PlannedExpense> planned) {
    }

    public record Line(String label, String category, BigDecimal amount, Source source) {
    }

    public record Month(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal net,
                        BigDecimal endingBalance, boolean shortfall, List<Line> lines) {
    }

    public record Result(YearMonth currentMonth, BigDecimal startingBalance, BigDecimal discretionaryMonthly,
                         List<Month> months) {
    }

    record Pattern(String key, String label, String category, Source source, BigDecimal amount, LocalDate last,
                   boolean active) {
    }

    static final int LOOKBACK_MONTHS = 24;
    static final int WINDOW_MONTHS = 6;
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.10");

    private ForecastEngine() {
    }

    public static Result forecast(Input in) {
        YearMonth current = in.currentMonth();
        LocalDate historyEnd = current.atDay(1).minusDays(1);
        List<HistoryTxn> history = in.history().stream()
                .filter(t -> t.date().isBefore(current.atDay(1)) && !t.date().isBefore(current.minusMonths(LOOKBACK_MONTHS).atDay(1)))
                .toList();

        List<Pattern> patterns = detect(history, current, historyEnd);
        BigDecimal discretionary = discretionary(history, patterns, in.monthlyNet(), current);
        List<Pattern> projected = patterns.stream()
                .filter(Pattern::active)
                .filter(p -> in.planned().stream().noneMatch(e -> replaces(e, p)))
                .toList();

        List<Month> months = new ArrayList<>();
        BigDecimal balance = scale(in.startingBalance());
        for (int k = 1; k <= in.horizonMonths(); k++) {
            YearMonth m = current.plusMonths(k);
            List<Line> lines = new ArrayList<>();
            for (Pattern p : projected) {
                BigDecimal amount = amountIn(p, m);
                if (amount.signum() != 0) {
                    lines.add(new Line(p.label(), p.category(), scale(amount), p.source()));
                }
            }
            for (PlannedExpense e : in.planned()) {
                if (e.activeIn(m)) {
                    lines.add(new Line(e.name(), e.category(), scale(e.amount().negate()), Source.RECURRING_EXPENSE));
                }
            }
            if (discretionary.signum() != 0) {
                lines.add(new Line("Everyday spending", "Discretionary", discretionary, Source.DISCRETIONARY));
            }
            BigDecimal income = lines.stream().map(Line::amount).filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal expenses = lines.stream().map(Line::amount).filter(a -> a.signum() < 0).reduce(BigDecimal.ZERO, BigDecimal::add).negate();
            BigDecimal net = income.subtract(expenses);
            balance = balance.add(net);
            months.add(new Month(m, scale(income), scale(expenses), scale(net), balance, balance.signum() < 0, List.copyOf(lines)));
        }
        return new Result(current, scale(in.startingBalance()), discretionary, months);
    }

    static List<Pattern> detect(List<HistoryTxn> history, YearMonth current, LocalDate historyEnd) {
        Map<String, List<HistoryTxn>> groups = history.stream()
                .sorted(Comparator.comparing(HistoryTxn::date))
                .collect(Collectors.groupingBy(HistoryTxn::key, LinkedHashMap::new, Collectors.toList()));
        List<Pattern> patterns = new ArrayList<>();
        groups.forEach((key, txns) -> classify(key, txns, current, historyEnd).ifPresent(patterns::add));
        return patterns;
    }

    private static Optional<Pattern> classify(String key, List<HistoryTxn> txns, YearMonth current, LocalDate historyEnd) {
        HistoryTxn last = txns.getLast();
        List<Long> intervals = new ArrayList<>();
        for (int i = Math.max(1, txns.size() - 7); i < txns.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(txns.get(i - 1).date(), txns.get(i).date()));
        }
        List<BigDecimal> recentAmounts = txns.subList(Math.max(0, txns.size() - 4), txns.size()).stream().map(HistoryTxn::amount).toList();

        if (txns.size() >= 2 && allBetween(intervals, 330, 400)) {
            boolean active = ChronoUnit.DAYS.between(last.date(), historyEnd) <= 400;
            return Optional.of(new Pattern(key, last.label(), last.category(), Source.ANNUAL, last.amount(), last.date(), active));
        }
        if (txns.size() >= 3 && allBetween(intervals, 6, 8)) {
            boolean active = !last.date().isBefore(historyEnd.minusDays(14));
            return Optional.of(new Pattern(key, last.label(), last.category(), Source.WEEKLY, median(recentAmounts), last.date(), active));
        }
        if (txns.size() >= 3 && allBetween(intervals, 13, 15)) {
            boolean active = !last.date().isBefore(historyEnd.minusDays(28));
            return Optional.of(new Pattern(key, last.label(), last.category(), Source.BIWEEKLY, median(recentAmounts), last.date(), active));
        }

        Map<YearMonth, BigDecimal> sums = monthSums(txns, current);
        boolean recent = sums.containsKey(current.minusMonths(1)) || sums.containsKey(current.minusMonths(2));
        if (sums.size() >= 4 && recent) {
            return Optional.of(new Pattern(key, last.label(), last.category(), Source.MONTHLY,
                    median(List.copyOf(sums.values())), last.date(), true));
        }
        if (txns.size() >= 4 && allBetween(intervals, 25, 35)) {
            // A monthly series that has stopped (e.g. a paid-off loan): not projected, not discretionary either.
            return Optional.of(new Pattern(key, last.label(), last.category(), Source.MONTHLY, median(recentAmounts), last.date(), false));
        }
        return Optional.empty();
    }

    private static Map<YearMonth, BigDecimal> monthSums(List<HistoryTxn> txns, YearMonth current) {
        YearMonth windowStart = current.minusMonths(WINDOW_MONTHS);
        return txns.stream()
                .filter(t -> !YearMonth.from(t.date()).isBefore(windowStart))
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.date()),
                        Collectors.reducing(BigDecimal.ZERO, HistoryTxn::amount, BigDecimal::add)));
    }

    static BigDecimal discretionary(List<HistoryTxn> history, List<Pattern> patterns, Map<YearMonth, BigDecimal> monthlyNet,
                                    YearMonth current) {
        Set<String> patterned = patterns.stream().map(Pattern::key).collect(Collectors.toSet());
        Optional<YearMonth> firstMonth = history.stream().map(t -> YearMonth.from(t.date())).min(Comparator.naturalOrder());
        List<BigDecimal> samples = new ArrayList<>();
        for (int k = 1; k <= WINDOW_MONTHS; k++) {
            YearMonth m = current.minusMonths(k);
            if (firstMonth.isEmpty() || m.isBefore(firstMonth.get())) {
                continue;
            }
            BigDecimal net = monthlyNet.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal structured = history.stream()
                    .filter(t -> patterned.contains(t.key()) && YearMonth.from(t.date()).equals(m))
                    .map(HistoryTxn::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            samples.add(net.subtract(structured));
        }
        return samples.isEmpty() ? scale(BigDecimal.ZERO) : scale(median(samples));
    }

    private static BigDecimal amountIn(Pattern p, YearMonth m) {
        return switch (p.source()) {
            case MONTHLY -> p.amount();
            case ANNUAL -> {
                long years = ChronoUnit.YEARS.between(YearMonth.from(p.last()), m);
                yield years >= 1 && YearMonth.from(p.last()).plusYears(years).equals(m) ? p.amount() : BigDecimal.ZERO;
            }
            case WEEKLY -> p.amount().multiply(BigDecimal.valueOf(occurrences(p.last(), 7, m)));
            case BIWEEKLY -> p.amount().multiply(BigDecimal.valueOf(occurrences(p.last(), 14, m)));
            default -> BigDecimal.ZERO;
        };
    }

    private static long occurrences(LocalDate last, int stepDays, YearMonth m) {
        long count = 0;
        for (LocalDate d = last.plusDays(stepDays); !d.isAfter(m.atEndOfMonth()); d = d.plusDays(stepDays)) {
            if (YearMonth.from(d).equals(m)) {
                count++;
            }
        }
        return count;
    }

    /** A user-entered expense replaces a detected outflow in the same category with a similar monthly amount. */
    private static boolean replaces(PlannedExpense e, Pattern p) {
        if (p.amount().signum() >= 0 || !p.category().equalsIgnoreCase(e.category())) {
            return false;
        }
        BigDecimal monthly = switch (p.source()) {
            case MONTHLY -> p.amount().negate();
            case WEEKLY -> p.amount().negate().multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case BIWEEKLY -> p.amount().negate().multiply(BigDecimal.valueOf(26)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> null;
        };
        return monthly != null
                && monthly.subtract(e.amount()).abs().compareTo(e.amount().multiply(MATCH_TOLERANCE)) <= 0;
    }

    private static boolean allBetween(List<Long> values, long min, long max) {
        return !values.isEmpty() && values.stream().allMatch(v -> v >= min && v <= max);
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
