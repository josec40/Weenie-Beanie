package com.shbean.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shbean.forecast.ForecastEngine.Month;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.NoOfferReason;
import com.shbean.loan.OfferEngine.Proposal;

class OfferEngineTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);
    private static final LoanProperties PROPS = new LoanProperties(new BigDecimal("0.05"),
            List.of(new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("1000")), new BigDecimal("25"), 1, 3);

    /** The demo forecast: +152 steady, insurance in month 4, bonus in month 6. */
    private static final int[] DEMO_NETS = {152, 152, 152, -1098, 152, 1352, 152, 152, 152, 152, 152, 152};

    @Test
    void noShortfallMeansNoOffer() {
        Decision d = OfferEngine.decide(months(1000, 100, 100, 100), 0, PROPS);
        assertThat(d.proposal()).isEmpty();
        assertThat(d.reason()).isEqualTo(NoOfferReason.NO_SHORTFALL);
    }

    @Test
    void demoShortfallExceedsTierZeroCap() {
        Decision d = OfferEngine.decide(months(300, DEMO_NETS), 0, PROPS);
        assertThat(d.proposal()).isEmpty();
        assertThat(d.reason()).isEqualTo(NoOfferReason.EXCEEDS_TIER_CAP);
    }

    @Test
    void tierOneCoversDemoShortfallAndRepaysInBonusMonth() {
        Proposal p = OfferEngine.decide(months(300, DEMO_NETS), 1, PROPS).proposal().orElseThrow();

        assertThat(p.shortfallMonth()).isEqualTo(NOW.plusMonths(4));
        assertThat(p.deficit()).isEqualByComparingTo("342");
        assertThat(p.principal()).isEqualByComparingTo("342");
        assertThat(p.fee()).isEqualByComparingTo("17.10");
        // Month 5 is still negative without the loan and its +152 does not cover 359.10; month 6 (+1352) does.
        assertThat(p.repaymentMonth()).isEqualTo(NOW.plusMonths(6));
        assertThat(p.projectedSurplus()).isEqualByComparingTo("1352");
        assertThat(p.tier()).isEqualTo(1);
    }

    @Test
    void deficitIsRoundedUpToWholeDollars() {
        Proposal p = OfferEngine.decide(months(0, -50, 400), 0, PROPS).proposal().orElseThrow();
        assertThat(p.principal()).isEqualByComparingTo("50");

        Proposal q = OfferEngine.decide(monthsExact(new BigDecimal("0"), new BigDecimal("-49.10"), new BigDecimal("400")), 0, PROPS)
                .proposal().orElseThrow();
        assertThat(q.principal()).isEqualByComparingTo("50");
        assertThat(q.fee()).isEqualByComparingTo("2.50");
    }

    @Test
    void noOfferWhenNoMonthInWindowCanRepay() {
        // Surplus only arrives in month 5 = shortfall + 4, outside the 3-month window.
        Decision d = OfferEngine.decide(months(0, -100, 20, 20, 20, 1000), 0, PROPS);
        assertThat(d.reason()).isEqualTo(NoOfferReason.NO_REPAYMENT_MONTH);
    }

    @Test
    void repaymentMonthMustStayNonNegativeAfterFee() {
        // Month 3 net (+170) covers deficit + fee (157.50) but the balance is still -180, so month 4 is chosen.
        Proposal p = OfferEngine.decide(months(0, -150, -200, 170, 400), 0, PROPS).proposal().orElseThrow();
        assertThat(p.repaymentMonth()).isEqualTo(NOW.plusMonths(4));
    }

    @Test
    void purchaseAffordableWhenStartingBalanceCoversAndWindowStaysPositive() {
        // Start 300, purchase 20 -> startAfterPurchase = 280, months stay positive
        Decision d = OfferEngine.decidePurchase(new BigDecimal("280"), months(280, 100, 100, 100), 1, PROPS);
        assertThat(d.proposal()).isEmpty();
        assertThat(d.reason()).isEqualTo(NoOfferReason.NO_SHORTFALL);
    }

    @Test
    void purchaseImmediateGapOffersLoan() {
        // Start 300, purchase 450 -> startAfterPurchase = -150, todayGap = 150
        // Month 1 (+152) net covers 150 + 7.50 fee = 157.50? No, 152 < 157.50.
        // Let's test with month 1 net = 200:
        Decision d = OfferEngine.decidePurchase(new BigDecimal("-150"), months(-150, 200, 150, 150), 1, PROPS);
        assertThat(d.proposal()).isPresent();
        Proposal p = d.proposal().get();
        assertThat(p.principal()).isEqualByComparingTo("150.00");
        assertThat(p.fee()).isEqualByComparingTo("7.50");
        assertThat(p.repaymentMonth()).isEqualTo(NOW.plusMonths(1));
    }

    @Test
    void purchaseCapExceededReturnsExceedsTierCap() {
        // Start 300, purchase 900 -> startAfterPurchase = -600, todayGap = 600 > 500 (Tier 1 cap)
        Decision d = OfferEngine.decidePurchase(new BigDecimal("-600"), months(-600, 1000, 1000, 1000), 1, PROPS);
        assertThat(d.proposal()).isEmpty();
        assertThat(d.reason()).isEqualTo(NoOfferReason.EXCEEDS_TIER_CAP);
    }

    @Test
    void purchaseNoRepaymentMonthWithinWindow() {
        // Gap 150, but candidate months only have +10 net
        Decision d = OfferEngine.decidePurchase(new BigDecimal("-150"), months(-150, 10, 10, 10), 1, PROPS);
        assertThat(d.proposal()).isEmpty();
        assertThat(d.reason()).isEqualTo(NoOfferReason.NO_REPAYMENT_MONTH);
    }

    @Test
    void purchaseIntermediateDipSizesPrincipalToWorstMonth() {
        // Today gap = 20 (startAfterPurchase = -20).
        // Month 1 has net -80 -> ending balance = -100.
        // Month 2 has net 300 -> ending balance = 200.
        // Repaying in month 2 must cover the intermediate dip (100), not just today's gap (20).
        Decision d = OfferEngine.decidePurchase(new BigDecimal("-20"), months(-20, -80, 300, 100), 1, PROPS);
        assertThat(d.proposal()).isPresent();
        Proposal p = d.proposal().get();
        assertThat(p.principal()).isEqualByComparingTo("100.00");
        assertThat(p.fee()).isEqualByComparingTo("5.00");
        assertThat(p.repaymentMonth()).isEqualTo(NOW.plusMonths(2));
    }

    @Test
    void purchaseFeeRoundingHalfUp() {
        // Gap 150.30 -> ceil(150.30) = 151.00 -> fee = 151.00 * 0.05 = 7.55
        Decision d = OfferEngine.decidePurchase(new BigDecimal("-150.30"), months(-151, 300, 100, 100), 1, PROPS);
        assertThat(d.proposal()).isPresent();
        Proposal p = d.proposal().get();
        assertThat(p.principal()).isEqualByComparingTo("151.00");
        assertThat(p.fee()).isEqualByComparingTo("7.55");
    }

    private static List<Month> months(int start, int... nets) {
        BigDecimal[] values = new BigDecimal[nets.length];
        for (int i = 0; i < nets.length; i++) {
            values[i] = BigDecimal.valueOf(nets[i]);
        }
        return monthsExact(BigDecimal.valueOf(start), values);
    }

    private static List<Month> monthsExact(BigDecimal start, BigDecimal... nets) {
        List<Month> out = new ArrayList<>();
        BigDecimal balance = start;
        for (int i = 0; i < nets.length; i++) {
            balance = balance.add(nets[i]);
            out.add(new Month(NOW.plusMonths(i + 1), BigDecimal.ZERO, BigDecimal.ZERO, nets[i], balance, balance.signum() < 0, List.of()));
        }
        return out;
    }
}
