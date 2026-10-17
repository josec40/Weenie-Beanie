package com.shbean.loan;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Credential tier for underwriting; Phase 8 maintains the credential row, users without one are Tier 0. */
@Component
public class CreditTiers {

    private final JdbcTemplate jdbc;

    public CreditTiers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int tierOf(UUID userId) {
        return jdbc.queryForList("SELECT tier FROM credential WHERE user_id = ?", Integer.class, userId)
                .stream().findFirst().orElse(0);
    }
}
