package com.shbean.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic transaction history for the demo persona "Alex".
 * <p>
 * Steady months net roughly +$160. Annual items recur 12 months after they appear in history, so relative to the
 * current month: car insurance (months -20, -8) drives a shortfall in month +4, and the annual bonus
 * (months -18, -6) produces a surplus in month +6. Nessie truncates amounts to whole dollars, so all amounts are whole.
 */
public final class DemoScenario {

    public static final int HISTORY_MONTHS = 20;
    public static final BigDecimal CURRENT_BALANCE = new BigDecimal("300");
    public static final int INSURANCE_MONTH_OFFSET = -8;
    public static final int BONUS_MONTH_OFFSET = -6;
    public static final int INSURANCE_PREMIUM = 1250;
    public static final int BONUS = 1200;

    public enum Kind { DEPOSIT, WITHDRAWAL, PURCHASE }

    public record MerchantSpec(String key, String name, String category) {
    }

    /** {@code merchantKey} is set only for purchases. */
    public record PlannedTxn(LocalDate date, Kind kind, String merchantKey, BigDecimal amount, String description) {
    }

    public static final List<MerchantSpec> MERCHANTS = List.of(
            new MerchantSpec("rent", "Sunrise Apartments", "Housing"),
            new MerchantSpec("carloan", "Metro Auto Finance", "Loans"),
            new MerchantSpec("student", "EduLoan Servicing", "Loans"),
            new MerchantSpec("power", "City Power & Water", "Utilities"),
            new MerchantSpec("phone", "Volt Wireless", "Utilities"),
            new MerchantSpec("netflix", "Netflix", "Entertainment"),
            new MerchantSpec("gym", "Peak Fitness", "Health"),
            new MerchantSpec("grocery", "FreshMart", "Groceries"),
            new MerchantSpec("cafe", "Bean & Brew Cafe", "Dining"),
            new MerchantSpec("fuel", "QuickFuel", "Transportation"),
            new MerchantSpec("shop", "TargetMart", "Shopping"),
            new MerchantSpec("insurance", "Shield Auto Insurance", "Insurance"));

    private DemoScenario() {
    }

    public static List<PlannedTxn> history(YearMonth currentMonth, long seed) {
        Random rnd = new Random(seed);
        List<PlannedTxn> txns = new ArrayList<>();
        for (int offset = -HISTORY_MONTHS; offset < 0; offset++) {
            YearMonth m = currentMonth.plusMonths(offset);

            txns.add(deposit(m.atDay(1), 1850, "Payroll - Acme Logistics"));
            txns.add(deposit(m.atDay(15), 1850, "Payroll - Acme Logistics"));

            txns.add(purchase(m.atDay(1), "rent", 1450, "Rent"));
            txns.add(purchase(m.atDay(3), "gym", 40, "Gym membership"));
            txns.add(purchase(m.atDay(5), "carloan", 385, "Car loan payment"));
            txns.add(purchase(m.atDay(7), "student", 120, "Student loan payment"));
            txns.add(purchase(m.atDay(10), "power", between(rnd, 110, 160), "Electric & water"));
            txns.add(purchase(m.atDay(12), "phone", 65, "Phone bill"));
            txns.add(purchase(m.atDay(18), "netflix", 15, "Netflix subscription"));

            txns.add(purchase(m.atDay(6), "grocery", between(rnd, 230, 290), "Groceries"));
            txns.add(purchase(m.atDay(20), "grocery", between(rnd, 230, 290), "Groceries"));
            txns.add(purchase(m.atDay(9), "cafe", between(rnd, 40, 70), "Coffee & lunch"));
            txns.add(purchase(m.atDay(16), "cafe", between(rnd, 40, 70), "Dinner out"));
            txns.add(purchase(m.atDay(24), "cafe", between(rnd, 40, 70), "Coffee & lunch"));
            txns.add(purchase(m.atDay(8), "fuel", between(rnd, 70, 95), "Gas"));
            txns.add(purchase(m.atDay(22), "fuel", between(rnd, 70, 95), "Gas"));
            txns.add(purchase(m.atDay(14), "shop", between(rnd, 330, 430), "Household & clothing"));
            txns.add(new PlannedTxn(m.atDay(26), Kind.WITHDRAWAL, null, BigDecimal.valueOf(100), "ATM withdrawal"));

            if ((offset - INSURANCE_MONTH_OFFSET) % 12 == 0) {
                txns.add(purchase(m.atDay(8), "insurance", INSURANCE_PREMIUM, "Annual auto insurance premium"));
            }
            if ((offset - BONUS_MONTH_OFFSET) % 12 == 0) {
                txns.add(deposit(m.atDay(15), BONUS, "Annual performance bonus"));
            }
        }
        return txns;
    }

    private static PlannedTxn deposit(LocalDate date, int amount, String description) {
        return new PlannedTxn(date, Kind.DEPOSIT, null, BigDecimal.valueOf(amount), description);
    }

    private static PlannedTxn purchase(LocalDate date, String merchantKey, int amount, String description) {
        return new PlannedTxn(date, Kind.PURCHASE, merchantKey, BigDecimal.valueOf(amount), description);
    }

    private static int between(Random rnd, int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }
}
