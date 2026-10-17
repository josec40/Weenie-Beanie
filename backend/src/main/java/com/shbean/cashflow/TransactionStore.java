package com.shbean.cashflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

    /** Returns the number of rows actually inserted (duplicates by Nessie id are skipped). */
    public int insertAll(List<TransactionRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate(INSERT_TXN, rows, 200, (ps, r) -> {
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
        return Arrays.stream(counts).flatMapToInt(Arrays::stream).filter(c -> c > 0).sum();
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

    public void audit(UUID userId, String type, String payloadJson) {
        jdbc.update("INSERT INTO audit_event (id, occurred_at, user_id, type, payload) VALUES (?, now(), ?, ?, ?::jsonb)",
                UUID.randomUUID(), userId, type, payloadJson);
    }
}
