package com.shbean.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shbean.ai.ExplanationService;
import com.shbean.ai.ExplanationService.Explanation;
import com.shbean.ai.ExplanationService.Source;
import com.shbean.cashflow.NessieSyncService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.cashflow.TransactionStore.BalanceSnapshot;
import com.shbean.events.EventPublisher;
import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.forecast.ForecastService;
import com.shbean.forecast.ForecastService.Forecast;
import com.shbean.loan.CreditTiers;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanProperties;
import com.shbean.loan.LoanRepository;
import com.shbean.nessie.NessieClient;
import com.shbean.user.AppUser;

class PurchaseServiceTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);
    private static final LoanProperties PROPS = new LoanProperties(new BigDecimal("0.05"),
            List.of(new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("1000")), new BigDecimal("25"), 1, 3);

    private ForecastService forecasts;
    private ExplanationService explanations;
    private LoanRepository loans;
    private CreditTiers tiers;
    private TransactionStore store;
    private NessieClient nessie;
    private NessieSyncService sync;
        private EventPublisher events;
    private PurchaseService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        forecasts = mock(ForecastService.class);
        explanations = mock(ExplanationService.class);
        loans = mock(LoanRepository.class);
        tiers = mock(CreditTiers.class);
        store = mock(TransactionStore.class);
        nessie = mock(NessieClient.class);
        sync = mock(NessieSyncService.class);
        events = mock(EventPublisher.class);

        service = new PurchaseService(forecasts, explanations, loans, tiers, PROPS, store, nessie, sync, events);

        user = new AppUser("alex@demo.local", "hash");
        user.linkNessie("cust-1", "acc-1");

        when(explanations.explainPurchase(any())).thenReturn(new Explanation("Sample explanation", Source.FALLBACK));
        when(tiers.tierOf(user.getId())).thenReturn(1); // Tier 1: cap $500
    }

    @Test
    void affordableWhenBalanceCoversAndNoShortfall() {
        when(store.latestBalance("acc-1")).thenReturn(Optional.of(new BalanceSnapshot(new BigDecimal("300.00"), Instant.now())));
        when(loans.findFirstByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(Optional.empty());

        List<Month> months = List.of(
                new Month(NOW.plusMonths(1), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("380"), false, List.of()),
                new Month(NOW.plusMonths(2), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("480"), false, List.of()),
                new Month(NOW.plusMonths(3), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("580"), false, List.of())
        );
        when(forecasts.forecast(eq(user), anyInt(), eq(new BigDecimal("20.00"))))
                .thenReturn(new Forecast(NOW, new BigDecimal("280.00"), Instant.now(), BigDecimal.ZERO, months));
        when(forecasts.forecast(eq(user), anyInt(), eq(BigDecimal.ZERO)))
                .thenReturn(new Forecast(NOW, new BigDecimal("300.00"), Instant.now(), BigDecimal.ZERO, months));

        PurchaseCheckResult result = service.purchaseCheck(user, new BigDecimal("20.00"), "Espresso Grinder");

        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.AFFORDABLE);
        assertThat(result.proposal()).isNull();
        assertThat(result.gapToday()).isEqualByComparingTo("0.00");
    }

    @Test
    void loanAvailableWhenGapCanBeRepaidWithinWindow() {
        when(store.latestBalance("acc-1")).thenReturn(Optional.of(new BalanceSnapshot(new BigDecimal("300.00"), Instant.now())));
        when(loans.findFirstByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(Optional.empty());

        // Purchase $450 -> gap $150. Month 1 (+200) covers 150 + 7.50 fee = 157.50
        List<Month> whatIfMonths = List.of(
                new Month(NOW.plusMonths(1), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("50"), false, List.of()),
                new Month(NOW.plusMonths(2), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("150"), false, List.of()),
                new Month(NOW.plusMonths(3), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("250"), false, List.of())
        );
        when(forecasts.forecast(eq(user), anyInt(), eq(new BigDecimal("450.00"))))
                .thenReturn(new Forecast(NOW, new BigDecimal("-150.00"), Instant.now(), BigDecimal.ZERO, whatIfMonths));
        when(forecasts.forecast(eq(user), anyInt(), eq(BigDecimal.ZERO)))
                .thenReturn(new Forecast(NOW, new BigDecimal("300.00"), Instant.now(), BigDecimal.ZERO, whatIfMonths));

        PurchaseCheckResult result = service.purchaseCheck(user, new BigDecimal("450.00"), "Nitro Keg");

        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.LOAN_AVAILABLE);
        assertThat(result.proposal()).isNotNull();
        assertThat(result.proposal().principal()).isEqualByComparingTo("150.00");
        assertThat(result.proposal().fee()).isEqualByComparingTo("7.50");
        assertThat(result.proposal().totalDue()).isEqualByComparingTo("157.50");
        assertThat(result.proposal().repaymentMonth()).isEqualTo(NOW.plusMonths(1).toString());
    }

    @Test
    void notEligibleWhenExceedsTierCap() {
        when(store.latestBalance("acc-1")).thenReturn(Optional.of(new BalanceSnapshot(new BigDecimal("300.00"), Instant.now())));
        when(loans.findFirstByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(Optional.empty());

        // Purchase $900 -> gap $600 > Tier 1 cap ($500)
        List<Month> whatIfMonths = List.of(
                new Month(NOW.plusMonths(1), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("400"), false, List.of())
        );
        when(forecasts.forecast(eq(user), anyInt(), eq(new BigDecimal("900.00"))))
                .thenReturn(new Forecast(NOW, new BigDecimal("-600.00"), Instant.now(), BigDecimal.ZERO, whatIfMonths));
        when(forecasts.forecast(eq(user), anyInt(), eq(BigDecimal.ZERO)))
                .thenReturn(new Forecast(NOW, new BigDecimal("300.00"), Instant.now(), BigDecimal.ZERO, whatIfMonths));

        PurchaseCheckResult result = service.purchaseCheck(user, new BigDecimal("900.00"), "Espresso Machine");

        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.NOT_ELIGIBLE);
        assertThat(result.reason()).isEqualTo("EXCEEDS_TIER_CAP");
        assertThat(result.proposal()).isNull();
    }

    @Test
    void notEligibleWhenUserHasOpenLoan() {
        when(store.latestBalance("acc-1")).thenReturn(Optional.of(new BalanceSnapshot(new BigDecimal("300.00"), Instant.now())));
        Loan openLoan = mock(Loan.class);
        when(loans.findFirstByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(Optional.of(openLoan));

        List<Month> months = List.of(
                new Month(NOW.plusMonths(1), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("50"), false, List.of())
        );
        when(forecasts.forecast(eq(user), anyInt(), any()))
                .thenReturn(new Forecast(NOW, new BigDecimal("-150.00"), Instant.now(), BigDecimal.ZERO, months));

        PurchaseCheckResult result = service.purchaseCheck(user, new BigDecimal("450.00"), "Nitro Keg");

        assertThat(result.verdict()).isEqualTo(PurchaseVerdict.NOT_ELIGIBLE);
        assertThat(result.reason()).isEqualTo("HAS_OPEN_LOAN");
        assertThat(result.proposal()).isNull();
    }

    @Test
    void invalidInputThrows() {
        assertThatThrownBy(() -> service.purchaseCheck(user, null, "Item"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchaseCheck(user, new BigDecimal("0"), "Item"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchaseCheck(user, new BigDecimal("-10"), "Item"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchaseCheck(user, new BigDecimal("10.123"), "Item"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.purchaseCheck(user, new BigDecimal("60000"), "Item"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
