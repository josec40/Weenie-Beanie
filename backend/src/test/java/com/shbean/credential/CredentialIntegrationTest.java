package com.shbean.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shbean.TestInfra;
import com.shbean.cashflow.TransactionStore;
import com.shbean.credential.CredentialStore.Credential;
import com.shbean.demo.DemoClock;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CredentialUpdateRequested;
import com.shbean.events.Events.RepaymentOutcome;
import com.shbean.events.Topics;
import com.shbean.loan.CreditTiers;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.Proposal;
import com.shbean.loan.OfferService;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/** Fiat-only reputation: outcomes from settlement update the credential row exactly once per loan. */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none",
        "nessie.sync.enabled=false",
        "app.settlement.retry-delay-ms=1000"
})
@AutoConfigureMockMvc
@Import(TestInfra.class)
class CredentialIntegrationTest {

    @MockitoBean NessieClient nessie;
    @Autowired OfferService offers;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepo;
    @Autowired AppUserRepository users;
    @Autowired TransactionStore store;
    @Autowired DemoClock clock;
    @Autowired CredentialStore credentials;
    @Autowired CredentialService credentialService;
    @Autowired CreditTiers tiers;
    @Autowired EventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void onTimeRepaymentIsCountedOnceAndRaisesTheTier() {
        AppUser user = linkedUser("ontime", "acct-ontime", "1000");
        credentials.save(new Credential(user.getId(), null, null, 0, 1, 0, 0, new BigDecimal("100.00"), null, null,
                Instant.now()));
        YearMonth current = clock.currentMonth();

        Loan loan = loans.accept(user, offer(user, current, current.plusMonths(1)).getId(), true);
        await().atMost(Duration.ofSeconds(30)).until(() -> loanStatus(loan) == Loan.Status.ACTIVE);
        loans.closeMonth(current.plusMonths(1));

        await().atMost(Duration.ofSeconds(30)).until(() -> credential(user).onTimeRepayments() == 2);
        Credential c = credential(user);
        assertThat(c.tier()).isEqualTo(1);
        assertThat(c.totalRepaid()).isEqualByComparingTo("310.00");
        assertThat(tiers.tierOf(user.getId())).isEqualTo(1);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(auditTypes(user.getId())).contains("credential.update.requested", "credential.updated"));

        // A redelivered request for the same loan is ignored.
        assertThat(credentialService.apply(user.getId(), loan.getId(), RepaymentOutcome.ON_TIME, new BigDecimal("210")))
                .isEmpty();
        events.publish(Topics.CREDENTIAL_UPDATE_REQUESTED, user.getId(),
                new CredentialUpdateRequested(loan.getId(), RepaymentOutcome.ON_TIME, new BigDecimal("210"), "x"));
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> credential(user).onTimeRepayments() == 2);
    }

    @Test
    void defaultIsRecordedAndCancelsTwoOnTimeRepayments() {
        AppUser user = linkedUser("default", "acct-default", "50");
        credentials.save(new Credential(user.getId(), null, null, 1, 2, 0, 0, new BigDecimal("300.00"), null, null,
                Instant.now()));
        YearMonth current = clock.currentMonth();

        Loan loan = loans.accept(user, offer(user, current, current.plusMonths(1)).getId(), true);
        await().atMost(Duration.ofSeconds(30)).until(() -> loanStatus(loan) == Loan.Status.ACTIVE);
        loans.closeMonth(current.plusMonths(1));
        await().atMost(Duration.ofSeconds(30)).until(() -> loanStatus(loan) == Loan.Status.LATE);
        loans.closeMonth(current.plusMonths(3));
        assertThat(loanStatus(loan)).isEqualTo(Loan.Status.DEFAULTED);

        await().atMost(Duration.ofSeconds(30)).until(() -> credential(user).defaults() == 1);
        assertThat(credential(user).tier()).isZero();
        assertThat(credential(user).totalRepaid()).isEqualByComparingTo("300.00");
    }

    @Test
    void publicVerificationNeedsNoTokenAndRejectsBadInput() throws Exception {
        mvc.perform(get("/api/public/credentials/not-an-address")).andExpect(status().isBadRequest());
        // Fiat-only mode has no on-chain credential to verify.
        mvc.perform(get("/api/public/credentials/9rKgpQgeCbDhMr6P7k1XgZnUM75DxM2rB6iXvggH5goQ"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/graphql")).andExpect(status().isUnauthorized());
    }

    private AppUser linkedUser(String name, String account, String balance) {
        AppUser u = new AppUser(name + "-" + UUID.randomUUID() + "@demo.local", "x");
        u.linkNessie("cust-" + name, account);
        u = users.save(u);
        store.insertBalanceSnapshot(account, Instant.now(), u.getId(), new BigDecimal(balance));
        when(nessie.listDeposits(account)).thenReturn(List.of());
        when(nessie.listWithdrawals(account)).thenReturn(List.of());
        when(nessie.listPurchases(account)).thenReturn(List.of());
        when(nessie.createDeposit(eq(account), any())).thenReturn(transfer("dep-" + name));
        when(nessie.createWithdrawal(eq(account), any())).thenReturn(transfer("wd-" + name));
        return u;
    }

    private com.shbean.loan.LoanOffer offer(AppUser user, YearMonth shortfall, YearMonth repay) {
        Proposal p = new Proposal(shortfall, new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("10.00"),
                repay, new BigDecimal("800.00"), 0);
        return offers.apply(user.getId(), null, new Decision(Optional.of(p), null), "explanation").open().orElseThrow();
    }

    private Credential credential(AppUser user) {
        return credentials.find(user.getId()).orElseThrow();
    }

    private Loan.Status loanStatus(Loan loan) {
        return loanRepo.findById(loan.getId()).orElseThrow().getStatus();
    }

    private List<String> auditTypes(UUID userId) {
        return jdbc.queryForList("SELECT type FROM audit_event WHERE user_id = ?", String.class, userId);
    }

    private static Transfer transfer(String id) {
        return new Transfer(id, null, "completed", "balance", BigDecimal.ZERO, null);
    }
}
