package com.shbean.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.shbean.TestInfra;
import com.shbean.cashflow.TransactionStore;
import com.shbean.demo.DemoClock;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanOffer;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.Proposal;
import com.shbean.loan.OfferService;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/** Saga ordering, persistence, compensation and resume, with the on-chain ledger and Nessie mocked. */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none",
        "nessie.sync.enabled=false",
        "app.settlement.retry-delay-ms=1000"
})
@Import(TestInfra.class)
class SettlementSagaIntegrationTest {

    @MockitoBean NessieClient nessie;
    @MockitoBean LoanLedger ledger;
    @Autowired OfferService offers;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepo;
    @Autowired AppUserRepository users;
    @Autowired TransactionStore store;
    @Autowired DemoClock clock;
    @Autowired SettlementSteps steps;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetLedger() {
        reset(ledger);
    }

    @Test
    void chainLegsRunBeforeFiatOnDisbursementAndAfterItOnRepayment() {
        AppUser user = linkedUser("order", "acct-order", "1000");
        YearMonth current = clock.currentMonth();
        when(nessie.createDeposit(eq("acct-order"), any())).thenReturn(transfer("dep-1"));
        when(nessie.createWithdrawal(eq("acct-order"), any())).thenReturn(transfer("wd-1"));
        when(ledger.disburse(any())).thenReturn("sig-disburse");
        when(ledger.repay(any())).thenReturn("sig-repay");

        Loan loan = loans.accept(user, offer(user, current, current.plusMonths(1)).getId(), true);
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.ACTIVE);

        Loan active = loanRepo.findById(loan.getId()).orElseThrow();
        assertThat(active.getSolanaDisburseSignature()).isEqualTo("sig-disburse");
        assertThat(active.getNessieDepositId()).isEqualTo("dep-1");
        InOrder disbursement = inOrder(ledger, nessie);
        disbursement.verify(ledger).open(any());
        disbursement.verify(ledger).disburse(any());
        disbursement.verify(nessie).createDeposit(eq("acct-order"), eq(new CreateTransferRequest("balance",
                current.atDay(1), "completed", new BigDecimal("200.00"), NessieLedger.disbursementDescription(loan.getId()))));

        loans.closeMonth(current.plusMonths(1));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.REPAID);

        Loan repaid = loanRepo.findById(loan.getId()).orElseThrow();
        assertThat(repaid.getSolanaRepaySignature()).isEqualTo("sig-repay");
        assertThat(repaid.getNessieWithdrawalId()).isEqualTo("wd-1");
        InOrder repayment = inOrder(nessie, ledger);
        repayment.verify(nessie).createWithdrawal(eq("acct-order"), any());
        repayment.verify(ledger).onRamp(any());
        repayment.verify(ledger).repay(any());
        assertThat(steps.find(loan.getId(), DisbursementSaga.DEPOSIT)).get()
                .extracting(SettlementSteps.Step::externalId).isEqualTo("dep-1");
        assertThat(steps.find(loan.getId(), RepaymentSaga.withdrawStep(1))).get()
                .extracting(SettlementSteps.Step::externalId).isEqualTo("wd-1");
    }

    @Test
    void failedOnChainRepaymentRefundsTheWithdrawalAndGoesLate() {
        AppUser user = linkedUser("comp", "acct-comp", "1000");
        YearMonth current = clock.currentMonth();
        when(nessie.createDeposit(eq("acct-comp"), any())).thenReturn(transfer("dep-2"));
        when(nessie.createWithdrawal(eq("acct-comp"), any())).thenReturn(transfer("wd-2"));
        when(ledger.repay(any())).thenThrow(new SettlementException("REPAY#1 failed on-chain: custom program error"));

        Loan loan = loans.accept(user, offer(user, current, current.plusMonths(1)).getId(), true);
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.ACTIVE);
        loans.closeMonth(current.plusMonths(1));

        // 5 attempts 1 s apart, then the DLT handler compensates.
        await().atMost(Duration.ofSeconds(60)).until(() -> status(loan) == Loan.Status.LATE);
        Loan late = loanRepo.findById(loan.getId()).orElseThrow();
        assertThat(late.getLastFailure()).isEqualTo(DisbursementSaga.SETTLEMENT_FAILED);
        assertThat(late.getNessieWithdrawalId()).isNull();
        verify(ledger).awaitRepay(any());
        verify(nessie).createWithdrawal(eq("acct-comp"), any());
        verify(nessie).createDeposit(eq("acct-comp"), eq(new CreateTransferRequest("balance",
                current.plusMonths(1).atEndOfMonth(), "completed", new BigDecimal("210.00"),
                NessieLedger.reversalDescription(loan.getId(), 1))));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(auditTypes(user.getId())).contains("loan.fiat.failed", "EVENT_DEAD_LETTERED"));
    }

    @Test
    void resumedRepaymentAdoptsTheExistingWithdrawalWithoutRecheckingTheBalance() {
        // Balance is far below the safeguard, but the withdrawal for this attempt already went through.
        AppUser user = linkedUser("resume", "acct-resume", "50");
        YearMonth current = clock.currentMonth();
        when(nessie.createDeposit(eq("acct-resume"), any())).thenReturn(transfer("dep-3"));

        Loan loan = loans.accept(user, offer(user, current, current.plusMonths(1)).getId(), true);
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.ACTIVE);
        when(nessie.listWithdrawals("acct-resume")).thenReturn(List.of(new Transfer("wd-earlier", null, "completed",
                "balance", new BigDecimal("210"), NessieLedger.repaymentDescription(loan.getId(), 1))));

        loans.closeMonth(current.plusMonths(1));
        await().atMost(Duration.ofSeconds(30)).until(() -> status(loan) == Loan.Status.REPAID);

        assertThat(loanRepo.findById(loan.getId()).orElseThrow().getNessieWithdrawalId()).isEqualTo("wd-earlier");
        verify(nessie, never()).createWithdrawal(eq("acct-resume"), any());
        verify(ledger).repay(argThat(l -> l.getId().equals(loan.getId())));
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
        return offers.apply(user.getId(), null, new Decision(Optional.of(p), null), "explanation").open().orElseThrow();
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
