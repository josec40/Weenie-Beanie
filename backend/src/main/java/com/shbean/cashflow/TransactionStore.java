package com.shbean.cashflow;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access to the TimescaleDB hypertables and the monthly_cashflow continuous aggregate. */
@Repository
public class TransactionStore {

    public record TransactionRow(String id, Instant occurredAt, UUID userId, String accountId, String kind,
                                 BigDecimal amount, String category, String description, String merchantId,
                                 String status) {
    }

    public record MonthlyCashflow(LocalDate month, String category, BigDecimal income, BigDecimal spend,
                                  BigDecimal net, long txnCount) {
    }

    public record BalanceSnapshot(BigDecimal balance, Instant capturedAt) {
    }

    private static final String INSERT_TXN = """
            INSERT INTO account_transaction
                (id, occurred_at, user_id, account_id, kind, amount, category, description, merchant_id, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id, occurred_at) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public TransactionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns only the rows that were actually inserted (duplicates by Nessie id are skipped). */
    public List<TransactionRow> insertNew(List<TransactionRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int batchSize = 200;
        int[][] counts = jdbc.batchUpdate(INSERT_TXN, rows, batchSize, (ps, r) -> {
            ps.setString(1, r.id());
            ps.setTimestamp(2, Timestamp.from(r.occurredAt()));
            ps.setObject(3, r.userId());
            ps.setString(4, r.accountId());
            ps.setString(5, r.kind());
            ps.setBigDecimal(6, r.amount());
            ps.setString(7, r.category());
            ps.setString(8, r.description());
            ps.setString(9, r.merchantId());
            ps.setString(10, r.status());
        });
        List<TransactionRow> inserted = new ArrayList<>();
        for (int b = 0; b < counts.length; b++) {
            for (int i = 0; i < counts[b].length; i++) {
                if (counts[b][i] > 0) {
                    inserted.add(rows.get(b * batchSize + i));
                }
            }
        }
        return inserted;
    }

    public Optional<BalanceSnapshot> latestBalance(String accountId) {
        return jdbc.query("""
                        SELECT balance, captured_at FROM balance_snapshot
                        WHERE account_id = ? ORDER BY captured_at DESC LIMIT 1
                        """,
                (rs, i) -> new BalanceSnapshot(rs.getBigDecimal("balance"), rs.getTimestamp("captured_at").toInstant()),
                accountId).stream().findFirst();
    }

    public List<TransactionRow> transactions(UUID userId, LocalDate fromInclusive, LocalDate toExclusive) {
        return jdbc.query("""
                        SELECT id, occurred_at, user_id, account_id, kind, amount, category, description, merchant_id, status
                        FROM account_transaction
                        WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ?
                        ORDER BY occurred_at, id
                        """,
                (rs, i) -> new TransactionRow(
                        rs.getString("id"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("account_id"),
                        rs.getString("kind"),
                        rs.getBigDecimal("amount"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("merchant_id"),
                        rs.getString("status")),
                userId, utc(fromInclusive), utc(toExclusive));
    }

    private static OffsetDateTime utc(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public void insertBalanceSnapshot(String accountId, Instant capturedAt, UUID userId, BigDecimal balance) {
        jdbc.update("""
                INSERT INTO balance_snapshot (account_id, captured_at, user_id, balance)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, captured_at) DO NOTHING
                """, accountId, Timestamp.from(capturedAt), userId, balance);
    }

    /** Must not run inside a transaction (TimescaleDB restriction). */
    public void refreshMonthlyCashflow(LocalDate fromMonth, LocalDate toMonthExclusive) {
        jdbc.update("CALL refresh_continuous_aggregate('monthly_cashflow', ?::timestamptz, ?::timestamptz)",
                fromMonth.atStartOfDay().atOffset(ZoneOffset.UTC),
                toMonthExclusive.atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    public List<MonthlyCashflow> monthlyCashflow(UUID userId) {
        return jdbc.query("""
                        SELECT month, category, income, spend, net, txn_count
                        FROM monthly_cashflow
                        WHERE user_id = ?
                        ORDER BY month, category
                        """,
                (rs, i) -> new MonthlyCashflow(
                        rs.getObject("month", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
                        rs.getString("category"),
                        rs.getBigDecimal("income"),
                        rs.getBigDecimal("spend"),
                        rs.getBigDecimal("net"),
                        rs.getLong("txn_count")),
                userId);
    }
}
