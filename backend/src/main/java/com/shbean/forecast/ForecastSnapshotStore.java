package com.shbean.forecast;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shbean.forecast.ForecastService.Forecast;

@Repository
public class ForecastSnapshotStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ForecastSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Saves the forecast unless it is identical to the user's latest snapshot; returns the new snapshot id. */
    public Optional<UUID> saveIfChanged(UUID userId, Forecast forecast) {
        String months = toJson(forecast.months());
        Boolean unchanged = jdbc.query("""
                        SELECT months = ?::jsonb AND starting_balance = ? AS same
                        FROM forecast_snapshot WHERE user_id = ? ORDER BY generated_at DESC LIMIT 1
                        """,
                rs -> rs.next() ? rs.getBoolean("same") : Boolean.FALSE,
                months, forecast.startingBalance(), userId);
        if (Boolean.TRUE.equals(unchanged)) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO forecast_snapshot (id, user_id, horizon_months, starting_balance, months)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, id, userId, forecast.months().size(), forecast.startingBalance(), months);
        return Optional.of(id);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize forecast", e);
        }
    }
}
