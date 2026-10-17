package com.shbean.demo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Simulated month clock (decision 7). The current month is the real UTC month plus a persisted offset that
 * {@code advanceMonth} increments. All month-based business logic (forecast, offers, loans) reads from here.
 */
@Component
public class DemoClock {

    private final JdbcTemplate jdbc;

    public DemoClock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public YearMonth currentMonth() {
        Integer offset = jdbc.queryForObject("SELECT month_offset FROM demo_clock WHERE id = 1", Integer.class);
        return YearMonth.now(ZoneOffset.UTC).plusMonths(offset == null ? 0 : offset);
    }

    public int offset() {
        Integer offset = jdbc.queryForObject("SELECT month_offset FROM demo_clock WHERE id = 1", Integer.class);
        return offset == null ? 0 : offset;
    }

    /** Moves the clock forward one month and returns the new current month. */
    public YearMonth advance() {
        jdbc.update("UPDATE demo_clock SET month_offset = month_offset + 1, updated_at = now() WHERE id = 1");
        return currentMonth();
    }

    public YearMonth scenarioAnchor() {
        LocalDate anchor = jdbc.queryForObject("SELECT scenario_anchor FROM demo_clock WHERE id = 1", LocalDate.class);
        return anchor == null ? YearMonth.now(ZoneOffset.UTC) : YearMonth.from(anchor);
    }

    public void setScenarioAnchor(YearMonth anchor) {
        jdbc.update("UPDATE demo_clock SET scenario_anchor = ? WHERE id = 1", anchor.atDay(1));
    }
}
