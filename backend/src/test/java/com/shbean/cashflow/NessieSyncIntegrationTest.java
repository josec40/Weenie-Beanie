package com.shbean.cashflow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.shbean.TestInfra;
import com.shbean.cashflow.TransactionStore.MonthlyCashflow;
import com.shbean.events.IdempotencyGuard;
import com.shbean.events.Topics;
import com.shbean.forecast.ForecastService;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.Purchase;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none",
        "nessie.sync.enabled=false"
})
@Import(TestInfra.class)
class NessieSyncIntegrationTest {

    @MockitoBean NessieClient nessie;
    @Autowired NessieSyncService sync;
    @Autowired TransactionStore store;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecurringExpenseService recurring;
    @Autowired ForecastService forecasts;
    @Autowired IdempotencyGuard guard;

    @Test
    void ingestsDeduplicatesAndAggregatesMonthly() {
        AppUser user = new AppUser("sync@demo.local", "x");
        user.linkNessie("cust-1", "acct-1");
        user = users.save(user);

        when(nessie.getAccount("acct-1")).thenReturn(new Account("acct-1", "Checking", "Main", 0, new BigDecimal("300"), "cust-1"));
        when(nessie.listDeposits("acct-1")).thenReturn(List.of(
                new Transfer("d1", LocalDate.of(2026, 1, 1), "completed", "balance", new BigDecimal("1850"), "Payroll"),
                new Transfer("d2", LocalDate.of(2026, 2, 1), "completed", "balance", new BigDecimal("1850"), "Payroll"),
                new Transfer("d3", LocalDate.of(2026, 2, 3), "cancelled", "balance", new BigDecimal("999"), "Ignored")));
        when(nessie.listWithdrawals("acct-1")).thenReturn(List.of(
                new Transfer("w1", LocalDate.of(2026, 1, 20), "completed", "balance", new BigDecimal("100"), "ATM")));
        when(nessie.listPurchases("acct-1")).thenReturn(List.of(
                new Purchase("p1", "m-rent", LocalDate.of(2026, 1, 1), "completed", "balance", new BigDecimal("1450"), "Rent"),
                new Purchase("p2", "m-rent", LocalDate.of(2026, 2, 1), "completed", "balance", new BigDecimal("1450"), "Rent")));
        when(nessie.getMerchant(anyString())).thenReturn(new Merchant("m-rent", "Sunrise Apartments", List.of("Housing")));

        var first = sync.sync(user);
        var second = sync.sync(user);

        assertThat(first.fetched()).isEqualTo(5);
        assertThat(first.inserted()).isEqualTo(5);
        assertThat(second.inserted()).isZero();
        verify(nessie, times(1)).getMerchant("m-rent");

        List<MonthlyCashflow> cashflow = store.monthlyCashflow(user.getId());
        assertThat(cashflow).extracting(MonthlyCashflow::month, MonthlyCashflow::category, MonthlyCashflow::net)
                .containsExactly(
                        tuple(LocalDate.of(2026, 1, 1), "Cash", new BigDecimal("-100.00")),
                        tuple(LocalDate.of(2026, 1, 1), "Housing", new BigDecimal("-1450.00")),
                        tuple(LocalDate.of(2026, 1, 1), "Income", new BigDecimal("1850.00")),
                        tuple(LocalDate.of(2026, 2, 1), "Housing", new BigDecimal("-1450.00")),
                        tuple(LocalDate.of(2026, 2, 1), "Income", new BigDecimal("1850.00")));

        Integer snapshots = jdbc.queryForObject("SELECT count(*) FROM balance_snapshot WHERE user_id = ?", Integer.class, user.getId());
        assertThat(snapshots).isEqualTo(2);
        assertThat(store.latestBalance("acct-1")).get().extracting(b -> b.balance()).isEqualTo(new BigDecimal("300.00"));

        // Kafka chain: transactions.ingested -> cashflow.changed -> forecast_snapshot + forecast.updated, all audited.
        UUID userId = user.getId();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(eventCount(userId, Topics.TRANSACTIONS_INGESTED)).isEqualTo(1);
            assertThat(eventCount(userId, Topics.CASHFLOW_CHANGED)).isEqualTo(1);
            assertThat(eventCount(userId, Topics.FORECAST_UPDATED)).isEqualTo(1);
            assertThat(forecastSnapshots(userId)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT payload->>'correlationId') FROM audit_event
                WHERE user_id = ? AND type IN ('transactions.ingested', 'cashflow.changed', 'forecast.updated')
                """, Integer.class, userId)).isEqualTo(1);

        // Nessie's balance never moves; newly posted transactions are added to the last snapshot.
        when(nessie.listDeposits("acct-1")).thenReturn(List.of(
                new Transfer("d1", LocalDate.of(2026, 1, 1), "completed", "balance", new BigDecimal("1850"), "Payroll"),
                new Transfer("d4", LocalDate.of(2026, 3, 1), "completed", "balance", new BigDecimal("500"), "Loan disbursement")));
        when(nessie.listPurchases("acct-1")).thenReturn(List.of(
                new Purchase("p3", "m-rent", LocalDate.of(2026, 3, 2), "completed", "balance", new BigDecimal("120"), "Rent fee")));

        assertThat(sync.sync(user).inserted()).isEqualTo(2);
        assertThat(store.latestBalance("acct-1")).get().extracting(b -> b.balance()).isEqualTo(new BigDecimal("680.00"));
        verify(nessie, times(1)).getAccount("acct-1");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(eventCount(userId, Topics.FORECAST_UPDATED)).isEqualTo(2);
            assertThat(forecastSnapshots(userId)).isEqualTo(2);
        });
    }

    @Test
    void idempotencyGuardRunsOncePerEventAndRollsBackMarkerOnFailure() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> guard.once("test", eventId, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(guard.once("test", eventId, calls::incrementAndGet)).contains(2);
        assertThat(guard.once("test", eventId, calls::incrementAndGet)).isEmpty();
        assertThat(guard.once("other-consumer", eventId, calls::incrementAndGet)).contains(3);
    }

    private int eventCount(UUID userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE user_id = ? AND type = ?", Integer.class, userId, type);
    }

    private int forecastSnapshots(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM forecast_snapshot WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void recurringExpensesAreScopedToOwnerAndFeedTheForecast() {
        AppUser owner = users.save(new AppUser("owner@demo.local", "x"));
        AppUser other = users.save(new AppUser("other@demo.local", "x"));
        YearMonth next = YearMonth.now(ZoneOffset.UTC).plusMonths(1);

        RecurringExpense netflix = recurring.create(owner,
                new RecurringExpenseService.Input(" Netflix ", new BigDecimal("15.49"), "Entertainment", 18, next.toString(), 2));

        assertThat(netflix.getName()).isEqualTo("Netflix");
        assertThat(netflix.getEndMonth()).isEqualTo(next.plusMonths(1));
        assertThat(recurring.list(other.getId())).isEmpty();
        assertThat(recurring.delete(other.getId(), netflix.getId())).isFalse();
        assertThat(recurring.update(other.getId(), netflix.getId(),
                new RecurringExpenseService.Input("x", BigDecimal.ONE, "x", 1, next.toString(), null))).isEmpty();
        assertThatThrownBy(() -> recurring.create(owner,
                new RecurringExpenseService.Input("Bad", new BigDecimal("-5"), "x", 1, next.toString(), null)))
                .isInstanceOf(IllegalArgumentException.class);

        var forecast = forecasts.forecast(owner, 4);
        assertThat(forecast.balanceAsOf()).isNull();
        assertThat(forecast.months()).extracting(m -> m.expenses())
                .containsExactly(new BigDecimal("15.49"), new BigDecimal("15.49"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        assertThat(forecast.months().get(1).endingBalance()).isEqualByComparingTo("-30.98");
        assertThat(forecast.months().get(1).shortfall()).isTrue();

        assertThat(recurring.delete(owner.getId(), netflix.getId())).isTrue();
        assertThat(recurring.list(owner.getId())).isEmpty();
    }
}
