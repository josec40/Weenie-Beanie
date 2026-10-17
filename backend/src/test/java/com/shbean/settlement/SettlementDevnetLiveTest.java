package com.shbean.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.shbean.TestInfra;
import com.shbean.cashflow.TransactionStore;
import com.shbean.demo.DemoClock;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.Proposal;
import com.shbean.loan.OfferService;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.solana.AnchorCodec;
import com.shbean.solana.AnchorCodec.LoanStatus;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaProperties;
import com.shbean.solana.SplToken;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;
import com.shbean.wallet.WalletService;

/**
 * Full disbursement + repayment through the real sagas against devnet (Nessie mocked, Postgres/Kafka in containers).
 * Spends ~0.005 SOL of the bank wallet per run. Opt in with {@code SHBEAN_SOLANA_LIVE=true}.
 */
@EnabledIfEnvironmentVariable(named = "SHBEAN_SOLANA_LIVE", matches = "true")
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long!!",
        "nessie.api-key=test",
        "nessie.base-url=http://localhost:1",
        "spring.ai.model.chat=none",
        "nessie.sync.enabled=false",
        "solana.enabled=true"
})
@Import(TestInfra.class)
class SettlementDevnetLiveTest {

    @DynamicPropertySource
    static void walletKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("solana.wallet-master-key", () -> Base64.getEncoder().encodeToString(key));
    }

    @MockitoBean NessieClient nessie;
    @Autowired OfferService offers;
    @Autowired LoanService loans;
    @Autowired LoanRepository loanRepo;
    @Autowired AppUserRepository users;
    @Autowired TransactionStore store;
    @Autowired DemoClock clock;
    @Autowired SolanaClient solana;
    @Autowired SolanaProperties props;
    @Autowired SolanaBank bank;
    @Autowired WalletService wallets;

    @Test
    void loanIsEscrowedDisbursedAndRepaidOnDevnet() {
        AppUser user = new AppUser("live-" + UUID.randomUUID() + "@demo.local", "x");
        user.linkNessie("cust-live", "acct-live");
        user = users.save(user);
        store.insertBalanceSnapshot("acct-live", Instant.now(), user.getId(), new BigDecimal("1000"));
        when(nessie.listDeposits("acct-live")).thenReturn(List.of());
        when(nessie.listWithdrawals("acct-live")).thenReturn(List.of());
        when(nessie.listPurchases("acct-live")).thenReturn(List.of());
        when(nessie.createDeposit(eq("acct-live"), any())).thenReturn(transfer("dep-live"));
        when(nessie.createWithdrawal(eq("acct-live"), any())).thenReturn(transfer("wd-live"));

        YearMonth current = clock.currentMonth();
        Proposal p = new Proposal(current, new BigDecimal("12.00"), new BigDecimal("12.00"), new BigDecimal("0.60"),
                current.plusMonths(1), new BigDecimal("100.00"), 0);
        UUID offerId = offers.apply(user.getId(), null, new Decision(Optional.of(p), null), "live").open().orElseThrow().getId();

        Loan loan = loans.accept(user, offerId, true);
        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(2))
                .until(() -> status(loan) == Loan.Status.ACTIVE);

        Loan active = loanRepo.findById(loan.getId()).orElseThrow();
        String borrowerToken = SplToken.associatedTokenAddress(solana, wallets.address(user.getId()).orElseThrow(),
                props.usdcMint());
        assertThat(active.getSolanaDisburseSignature()).isNotBlank();
        assertThat(onChainStatus(loan)).isEqualTo(LoanStatus.DISBURSED);
        assertThat(bank.tokenBalance(borrowerToken)).isEqualTo(12_000_000L);

        loans.closeMonth(current.plusMonths(1));
        await().atMost(Duration.ofMinutes(4)).pollInterval(Duration.ofSeconds(2))
                .until(() -> status(loan) == Loan.Status.REPAID);

        Loan repaid = loanRepo.findById(loan.getId()).orElseThrow();
        assertThat(repaid.getSolanaRepaySignature()).isNotBlank();
        assertThat(onChainStatus(loan)).isEqualTo(LoanStatus.REPAID);
        assertThat(bank.tokenBalance(borrowerToken)).isZero();
        System.out.println("Disbursed: " + props.explorerTxUrl(repaid.getSolanaDisburseSignature()));
        System.out.println("Repaid:    " + props.explorerTxUrl(repaid.getSolanaRepaySignature()));
    }

    private LoanStatus onChainStatus(Loan loan) {
        return AnchorCodec.decodeLoan(solana.getAccount(bank.microloan().loanAddress(loan.getId())).orElseThrow().data())
                .status();
    }

    private Loan.Status status(Loan loan) {
        return loanRepo.findById(loan.getId()).orElseThrow().getStatus();
    }

    private static Transfer transfer(String id) {
        return new Transfer(id, null, "completed", "balance", BigDecimal.ZERO, null);
    }
}
