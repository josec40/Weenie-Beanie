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

    public enum CashflowReason { TRANSACTIONS, RECURRING_EXPENSE }

    public record CashflowChanged(CashflowReason reason) {
    }

    public record ForecastUpdated(UUID snapshotId, String currentMonth, BigDecimal startingBalance,
                                  List<String> shortfallMonths, BigDecimal lowestBalance) {
    }
}
