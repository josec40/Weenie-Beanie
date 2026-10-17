package com.shbean.loan;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shbean.ai.ExplanationService;
import com.shbean.ai.ExplanationService.OfferFacts;
import com.shbean.forecast.ForecastService;
import com.shbean.forecast.ForecastService.Forecast;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.loan.OfferEngine.Proposal;
import com.shbean.user.AppUser;

/** Keeps at most one open offer per user in line with the latest forecast. */
@Service
public class OfferService {

    /** Result of a refresh: the offer that is now open (new or unchanged), or empty. */
    public record Refresh(Optional<LoanOffer> open, boolean created, Decision decision) {
    }

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final ForecastService forecasts;
    private final LoanOfferRepository offers;
    private final LoanRepository loans;
    private final CreditTiers tiers;
    private final ExplanationService explanations;
    private final LoanProperties props;

    public OfferService(ForecastService forecasts, LoanOfferRepository offers, LoanRepository loans, CreditTiers tiers,
                        ExplanationService explanations, LoanProperties props) {
        this.forecasts = forecasts;
        this.offers = offers;
        this.loans = loans;
        this.tiers = tiers;
        this.explanations = explanations;
        this.props = props;
    }

    /** Explanation is generated outside the DB transaction (it may call Gemini). */
    public String explain(Proposal p) {
        return explanations.explainOffer(new OfferFacts(p.shortfallMonth().toString(), p.deficit(),
                p.principal(), p.fee(), p.repaymentMonth().toString(), p.projectedSurplus())).text();
    }

    public Decision decide(AppUser user) {
        Forecast f = forecasts.forecast(user, 12);
        return OfferEngine.decide(f.months(), tiers.tierOf(user.getId()), props);
    }

    @Transactional
    public Refresh apply(UUID userId, UUID snapshotId, Decision decision, String explanation) {
        if (loans.findFirstByUserIdAndStatusIn(userId, Loan.OPEN).isPresent()) {
            return new Refresh(Optional.empty(), false, decision);
        }
        Optional<LoanOffer> current = offers.findFirstByUserIdAndStatus(userId, LoanOffer.Status.OFFERED);
        if (decision.proposal().isEmpty()) {
            current.ifPresent(o -> o.close(LoanOffer.Status.EXPIRED));
            log.info("No offer for user {}: {}", userId, decision.reason());
            return new Refresh(Optional.empty(), false, decision);
        }
        Proposal p = decision.proposal().get();
        if (current.isPresent() && current.get().sameTerms(p)) {
            return new Refresh(current, false, decision);
        }
        current.ifPresent(o -> {
            o.close(LoanOffer.Status.SUPERSEDED);
            offers.saveAndFlush(o);
        });
        LoanOffer created = offers.save(new LoanOffer(userId, snapshotId, p, explanation));
        return new Refresh(Optional.of(created), true, decision);
    }
}
