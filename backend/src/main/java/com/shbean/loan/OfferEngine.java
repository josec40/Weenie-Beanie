package com.shbean.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.shbean.forecast.ForecastEngine.Month;

/**
 * Deterministic underwriting (decision 3): find the first projected shortfall month m with deficit D, then the
 * first month n in m+1..m+window whose projected net covers D + fee and whose balance stays non-negative after
 * repayment. The offer is made only if the tier cap covers the full deficit (principal = ceil(D)).
 */
public final class OfferEngine {

    public record Proposal(YearMonth shortfallMonth, BigDecimal deficit, BigDecimal principal, BigDecimal fee,
                           YearMonth repaymentMonth, BigDecimal projectedSurplus, int tier) {
    }

    public enum NoOfferReason { NO_SHORTFALL, EXCEEDS_TIER_CAP, NO_REPAYMENT_MONTH }

    public record Decision(Optional<Proposal> proposal, NoOfferReason reason) {
        static Decision offer(Proposal p) {
            return new Decision(Optional.of(p), null);
        }

        static Decision none(NoOfferReason r) {
            return new Decision(Optional.empty(), r);
        }
    }

    private OfferEngine() {
    }

    public static Decision decide(List<Month> months, int tier, LoanProperties props) {
        int m = -1;
        for (int i = 0; i < months.size(); i++) {
            if (months.get(i).shortfall()) {
                m = i;
                break;
            }
        }
        if (m < 0) {
            return Decision.none(NoOfferReason.NO_SHORTFALL);
        }
        Month shortfall = months.get(m);
        BigDecimal deficit = shortfall.endingBalance().negate().setScale(0, RoundingMode.CEILING).setScale(2);
        BigDecimal cap = props.capFor(tier);
        if (deficit.compareTo(cap) > 0) {
            return Decision.none(NoOfferReason.EXCEEDS_TIER_CAP);
        }
        BigDecimal principal = deficit;
        BigDecimal fee = principal.multiply(props.feeRate()).setScale(2, RoundingMode.HALF_UP);

        for (int j = m + 1; j <= Math.min(m + props.repaymentWindowMonths(), months.size() - 1); j++) {
            Month n = months.get(j);
            boolean covers = n.net().compareTo(deficit.add(fee)) >= 0;
            boolean staysPositive = n.endingBalance().subtract(fee).signum() >= 0;
            if (covers && staysPositive) {
                return Decision.offer(new Proposal(shortfall.month(), deficit, principal, fee, n.month(), n.net(), tier));
            }
        }
        return Decision.none(NoOfferReason.NO_REPAYMENT_MONTH);
    }
}
