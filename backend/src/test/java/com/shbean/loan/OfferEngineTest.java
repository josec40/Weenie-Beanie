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
