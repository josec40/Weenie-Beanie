package com.shbean.cashflow;

import com.shbean.cashflow.TransactionStore.MonthlyCashflow;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.Purchase;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "nessie.sync.enabled=false"
})
class NessieSyncIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg17").asCompatibleSubstituteFor("postgres"));

    @MockitoBean NessieClient nessie;
    @Autowired NessieSyncService sync;
    @Autowired TransactionStore store;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

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
        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE user_id = ? AND type = 'NESSIE_SYNCED'", Integer.class, user.getId());
        assertThat(snapshots).isEqualTo(2);
        assertThat(audits).isEqualTo(2);
    }
}
