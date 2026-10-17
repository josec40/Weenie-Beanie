package com.shbean.loan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import com.shbean.demo.DemoService;
import com.shbean.demo.DemoService.ClockView;
import com.shbean.loan.OfferEngine.Decision;
import com.shbean.solana.SolanaProperties;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

@Controller
public class LoanController {

    /** Why the customer does or does not see an offer right now. */
    public record CreditProfile(int tier, BigDecimal cap, String offerStatus) {
    }

    private final LoanService loans;
    private final OfferService offerService;
    private final LoanOfferRepository offers;
    private final LoanRepository loanRepo;
    private final CreditTiers tiers;
    private final LoanProperties props;
    private final DemoService demo;
    private final AppUserRepository users;
    private final SolanaProperties solana;

    public LoanController(LoanService loans, OfferService offerService, LoanOfferRepository offers, LoanRepository loanRepo,
                          CreditTiers tiers, LoanProperties props, DemoService demo, AppUserRepository users,
                          SolanaProperties solana) {
        this.loans = loans;
        this.offerService = offerService;
        this.offers = offers;
        this.loanRepo = loanRepo;
        this.tiers = tiers;
        this.props = props;
        this.demo = demo;
        this.users = users;
        this.solana = solana;
    }

    @QueryMapping
    public LoanOffer currentOffer(@AuthenticationPrincipal Jwt jwt) {
        return offers.findFirstByUserIdAndStatus(userId(jwt), LoanOffer.Status.OFFERED).orElse(null);
    }

    @QueryMapping
    public List<Loan> loans(@AuthenticationPrincipal Jwt jwt) {
        return loans.list(userId(jwt));
    }

    @QueryMapping
    public CreditProfile creditProfile(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = user(jwt);
        int tier = tiers.tierOf(user.getId());
        String status;
        if (loanRepo.findFirstByUserIdAndStatusIn(user.getId(), Loan.OPEN).isPresent()) {
            status = "HAS_OPEN_LOAN";
        } else {
            Decision d = offerService.decide(user);
            status = d.proposal().isPresent() ? "OFFER_AVAILABLE" : d.reason().name();
        }
        return new CreditProfile(tier, props.capFor(tier), status);
    }

    @QueryMapping
    public ClockView demoClock() {
        return demo.view();
    }

    @SchemaMapping(typeName = "Loan")
    public String disburseTxUrl(Loan loan) {
        return loan.getSolanaDisburseSignature() == null ? null : solana.explorerTxUrl(loan.getSolanaDisburseSignature());
    }

    @SchemaMapping(typeName = "Loan")
    public String repayTxUrl(Loan loan) {
        return loan.getSolanaRepaySignature() == null ? null : solana.explorerTxUrl(loan.getSolanaRepaySignature());
    }

    @MutationMapping
    public Loan acceptOffer(@Argument UUID offerId, @Argument boolean consent, @AuthenticationPrincipal Jwt jwt) {
        return loans.accept(user(jwt), offerId, consent);
    }

    @MutationMapping
    public boolean declineOffer(@Argument UUID offerId, @AuthenticationPrincipal Jwt jwt) {
        return loans.decline(userId(jwt), offerId);
    }

    @MutationMapping
    public Loan rescheduleLoan(@Argument UUID loanId, @AuthenticationPrincipal Jwt jwt) {
        return loans.reschedule(userId(jwt), loanId);
    }

    @MutationMapping
    public Loan repayLoanEarly(@Argument UUID loanId, @AuthenticationPrincipal Jwt jwt) {
        return loans.repayEarly(userId(jwt), loanId);
    }

    @MutationMapping
    public ClockView advanceMonth(@AuthenticationPrincipal Jwt jwt) {
        return demo.advanceMonth(user(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private AppUser user(Jwt jwt) {
        return users.findById(userId(jwt)).orElseThrow(() -> new IllegalArgumentException("unknown user"));
    }
}
