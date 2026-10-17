package com.shbean.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Durable saga log ({@code settlement_step}). Every write commits on its own (no surrounding transaction), so a
 * signature recorded here survives a crash that happens while the transaction is in flight.
 */
@Component
public class SettlementSteps {

    public enum State { PENDING, DONE, FAILED }

    public record Step(State state, String signature, String blockhash, String externalId, String detail) {
    }

    private static final int DETAIL_LIMIT = 255;

    private final JdbcTemplate jdbc;

    public SettlementSteps(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Step> find(UUID loanId, String step) {
        List<Step> rows = jdbc.query("SELECT status, tx_signature, blockhash, external_id, detail FROM settlement_step "
                        + "WHERE loan_id = ? AND step = ?",
                (rs, i) -> new Step(State.valueOf(rs.getString("status")), rs.getString("tx_signature"),
                        rs.getString("blockhash"), rs.getString("external_id"), rs.getString("detail")),
                loanId, step);
        return rows.stream().findFirst();
    }

    /** Recorded before the transaction is submitted. */
    public void pending(UUID loanId, String step, String signature, String blockhash) {
        upsert(loanId, step, State.PENDING, signature, blockhash, null, null);
    }

    public void done(UUID loanId, String step, String signature, String externalId) {
        upsert(loanId, step, State.DONE, signature, null, externalId, null);
    }

    public void failed(UUID loanId, String step, String detail) {
        jdbc.update("""
                INSERT INTO settlement_step (loan_id, step, status, detail, updated_at) VALUES (?, ?, 'FAILED', ?, now())
                ON CONFLICT (loan_id, step) DO UPDATE SET status = 'FAILED', detail = EXCLUDED.detail, updated_at = now()
                """, loanId, step, truncate(detail));
    }

    private void upsert(UUID loanId, String step, State state, String signature, String blockhash, String externalId,
                        String detail) {
        jdbc.update("""
                INSERT INTO settlement_step (loan_id, step, status, tx_signature, blockhash, external_id, detail, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (loan_id, step) DO UPDATE SET status = EXCLUDED.status, tx_signature = EXCLUDED.tx_signature,
                    blockhash = EXCLUDED.blockhash, external_id = EXCLUDED.external_id, detail = EXCLUDED.detail,
                    updated_at = now()
                """, loanId, step, state.name(), signature, blockhash, externalId, truncate(detail));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= DETAIL_LIMIT ? s : s.substring(0, DETAIL_LIMIT);
    }
}
