package com.shbean.credential;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The {@code credential} row: mirror of the on-chain Reputation PDA (or the local record in fiat-only mode). */
@Component
public class CredentialStore {

    public record Credential(UUID userId, String wallet, String reputationAddress, int tier, int onTimeRepayments,
                             int lateRepayments, int defaults, BigDecimal totalRepaid, String sbtMint,
                             String sbtMintSignature, Instant updatedAt) {

        public static Credential empty(UUID userId) {
            return new Credential(userId, null, null, 0, 0, 0, 0, BigDecimal.ZERO, null, null, null);
        }
    }

    private final JdbcTemplate jdbc;

    public CredentialStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credential> find(UUID userId) {
        List<Credential> rows = jdbc.query("""
                SELECT user_id, wallet_public_key, reputation_address, tier, on_time_repayments, late_repayments,
                       defaults, total_repaid, sbt_mint, sbt_mint_sig, updated_at
                FROM credential WHERE user_id = ?
                """, (rs, i) -> new Credential(rs.getObject("user_id", UUID.class), rs.getString("wallet_public_key"),
                rs.getString("reputation_address"), rs.getInt("tier"), rs.getInt("on_time_repayments"),
                rs.getInt("late_repayments"), rs.getInt("defaults"), rs.getBigDecimal("total_repaid"),
                rs.getString("sbt_mint"), rs.getString("sbt_mint_sig"), rs.getTimestamp("updated_at").toInstant()), userId);
        return rows.stream().findFirst();
    }

    public void save(Credential c) {
        jdbc.update("""
                INSERT INTO credential (user_id, wallet_public_key, reputation_address, tier, on_time_repayments,
                                        late_repayments, defaults, total_repaid, sbt_mint, sbt_mint_sig, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET wallet_public_key = EXCLUDED.wallet_public_key,
                    reputation_address = EXCLUDED.reputation_address, tier = EXCLUDED.tier,
                    on_time_repayments = EXCLUDED.on_time_repayments, late_repayments = EXCLUDED.late_repayments,
                    defaults = EXCLUDED.defaults, total_repaid = EXCLUDED.total_repaid, sbt_mint = EXCLUDED.sbt_mint,
                    sbt_mint_sig = EXCLUDED.sbt_mint_sig, updated_at = EXCLUDED.updated_at
                """, c.userId(), c.wallet(), c.reputationAddress(), c.tier(), c.onTimeRepayments(), c.lateRepayments(),
                c.defaults(), c.totalRepaid(), c.sbtMint(), c.sbtMintSignature(),
                Timestamp.from(c.updatedAt() == null ? Instant.now() : c.updatedAt()));
    }
}
