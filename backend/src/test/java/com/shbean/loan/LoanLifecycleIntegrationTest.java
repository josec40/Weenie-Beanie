package com.shbean.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.shbean.TestInfra;
import com.shbean.cashflow.TransactionStore;
import com.shbean.demo.DemoClock;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.Proposal;
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
class LoanLifecycleIntegrationTest {

    @MockitoBean NessieClient nessie;
    @Autowired OfferService offers;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepo;
    @Autowired LoanOfferRepository offerRepo;
    @Autowired AppUserRepository users;
    @Autowired TransactionStore store;
    @Autowired DemoClock clock;
    @Autowired JdbcTemplate jdbc;

    @Test
    void acceptDisburseAndAutoRepay() {
        AppUser user = linkedUser("happy", "acct-happy", "1000");
        YearMonth current = clock.currentMonth();
        LoanOffer offer = offer(user, current.plusMonths(1), current.plusMonths(3));
        when(nessie.createDeposit(eq("acct-happy"), any())).thenReturn(transfer("dep-1"));
        when(nessie.createWithdrawal(eq("acct-happy"), any())).thenReturn(transfer("wd-1"));

        assertThatThrownBy(() -> loans.accept(user, offer.getId(), false)).hasMessageContaining("consent");
        Loan loan = loans.accept(user, offer.getId(), true);
        assertThat(loan.getStatus()).isEqualTo(Loan.Status.ACCEPTED);
        assertThat(offerRepo.findById(offer.getId()).orElseThrow().getStatus()).isEqualTo(LoanOffer.Status.ACCEPTED);
        assertThatThrownBy(() -> loans.accept(user, offer.getId(), true)).hasMessageContaining("no longer available");

        loans.openMonth(current.plusMonths(1));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.ACTIVE);
        assertThat(loanRepo.findById(loan.getId()).orElseThrow().getNessieDepositId()).isEqualTo("dep-1");

        loans.closeMonth(current.plusMonths(2));
        assertThat(status(loan)).isEqualTo(Loan.Status.ACTIVE);

        loans.closeMonth(current.plusMonths(3));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.REPAID);
        verify(nessie).createWithdrawal(eq("acct-happy"),
                eq(new CreateTransferRequest("balance", current.plusMonths(3).atEndOfMonth(), "completed",
                        new BigDecimal("210.00"), "ShBean micro-loan repayment")));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(auditTypes(user.getId())).contains(
                "loan.accepted", "loan.disbursement.requested", "loan.disbursed", "loan.fiat.settled",
                "loan.repayment.requested", "loan.repaid"));
    }

    @Test
    void insufficientBalanceGoesLateThenDefaultsAfterGrace() {
        AppUser user = linkedUser("short", "acct-short", "100");
        YearMonth current = clock.currentMonth();
        LoanOffer offer = offer(user, current, current.plusMonths(1));
        when(nessie.createDeposit(eq("acct-short"), any())).thenReturn(transfer("dep-2"));

        Loan loan = loans.accept(user, offer.getId(), true);
        // Shortfall month is the current month, so disbursement starts immediately.
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.ACTIVE);

        loans.closeMonth(current.plusMonths(1));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.LATE);
        Loan late = loanRepo.findById(loan.getId()).orElseThrow();
        assertThat(late.getLastFailure()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(late.getGraceUntilMonth()).isEqualTo(current.plusMonths(2));

        loans.closeMonth(current.plusMonths(2));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.LATE
                && auditTypes(user.getId()).stream().filter("loan.fiat.failed"::equals).count() == 2);

        loans.closeMonth(current.plusMonths(3));
        assertThat(status(loan)).isEqualTo(Loan.Status.DEFAULTED);
        verify(nessie, never()).createWithdrawal(eq("acct-short"), any());
    }

    private AppUser linkedUser(String name, String account, String balance) {
        AppUser u = new AppUser(name + "-" + UUID.randomUUID() + "@demo.local", "x");
        u.linkNessie("cust-" + name, account);
        u = users.save(u);
        store.insertBalanceSnapshot(account, Instant.now(), u.getId(), new BigDecimal(balance));
        when(nessie.listDeposits(account)).thenReturn(List.of());
        when(nessie.listWithdrawals(account)).thenReturn(List.of());
        when(nessie.listPurchases(account)).thenReturn(List.of());
        return u;
    }

    private LoanOffer offer(AppUser user, YearMonth shortfall, YearMonth repay) {
        Proposal p = new Proposal(shortfall, new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("10.00"),
                repay, new BigDecimal("800.00"), 0);
        return offers.apply(user.getId(), null, Decision.offer(p), "explanation").open().orElseThrow();
    }

    private Loan.Status status(Loan loan) {
        return loanRepo.findById(loan.getId()).orElseThrow().getStatus();
    }

    private List<String> auditTypes(UUID userId) {
        return jdbc.queryForList("SELECT type FROM audit_event WHERE user_id = ?", String.class, userId);
    }

    private static Transfer transfer(String id) {
        return new Transfer(id, null, "completed", "balance", BigDecimal.ZERO, null);
    }
}
