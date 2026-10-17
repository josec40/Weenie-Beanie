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

    public static Decision decidePurchase(BigDecimal startAfterPurchase, List<Month> months, int tier, LoanProperties props) {
        BigDecimal todayGap = startAfterPurchase.signum() < 0 ? startAfterPurchase.negate() : BigDecimal.ZERO;
        int maxJ = Math.min(props.repaymentWindowMonths(), months.size());
        if (maxJ <= 0) {
            return Decision.none(NoOfferReason.NO_REPAYMENT_MONTH);
        }

        boolean hasAnyShortfallInWindow = todayGap.signum() > 0;
        if (!hasAnyShortfallInWindow) {
            for (int k = 0; k < maxJ; k++) {
                if (months.get(k).endingBalance().signum() < 0) {
                    hasAnyShortfallInWindow = true;
                    break;
                }
            }
        }
        if (!hasAnyShortfallInWindow) {
            return Decision.none(NoOfferReason.NO_SHORTFALL);
        }

        BigDecimal cap = props.capFor(tier);
        boolean anyWithinCap = false;
        YearMonth currentMonth = months.get(0).month().minusMonths(1);

        for (int j = 1; j <= maxJ; j++) {
            Month candidate = months.get(j - 1);
            BigDecimal maxDip = BigDecimal.ZERO;
            for (int k = 0; k < j - 1; k++) {
                BigDecimal bal = months.get(k).endingBalance();
                if (bal.signum() < 0) {
                    maxDip = maxDip.max(bal.negate());
                }
            }
            BigDecimal rawP = todayGap.max(maxDip);
            BigDecimal p = rawP.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.UNNECESSARY);

            if (p.compareTo(cap) <= 0) {
                anyWithinCap = true;
                BigDecimal fee = p.multiply(props.feeRate()).setScale(2, RoundingMode.HALF_UP);
                boolean covers = candidate.net().compareTo(p.add(fee)) >= 0;
                boolean staysPositive = candidate.endingBalance().subtract(fee).signum() >= 0;
                if (covers && staysPositive) {
                    return Decision.offer(new Proposal(currentMonth, rawP, p, fee, candidate.month(), candidate.net(), tier));
                }
            }
        }

        if (!anyWithinCap) {
            return Decision.none(NoOfferReason.EXCEEDS_TIER_CAP);
        }
        return Decision.none(NoOfferReason.NO_REPAYMENT_MONTH);
    }
}
