package com.shbean.loan;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.demo.DemoClock;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.LoanEvent;
import com.shbean.events.Topics;
import com.shbean.user.AppUser;

/**
 * Customer actions and month-boundary steps of the loan lifecycle. State changes commit first; events are published
 * after commit. Settlement (money movement) happens asynchronously in {@code com.shbean.settlement}.
 */
@Service
public class LoanService {

    record Pending(String topic, Loan loan, YearMonth month, String reason) {
    }

    private final LoanRepository loans;
    private final LoanOfferRepository offers;
    private final DemoClock clock;
    private final LoanProperties props;
    private final EventPublisher events;
    private final TransactionTemplate tx;

    public LoanService(LoanRepository loans, LoanOfferRepository offers, DemoClock clock, LoanProperties props,
                       EventPublisher events, TransactionTemplate tx) {
        this.loans = loans;
        this.offers = offers;
        this.clock = clock;
        this.props = props;
        this.events = events;
        this.tx = tx;
    }

    public List<Loan> list(UUID userId) {
        return loans.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Loan accept(AppUser user, UUID offerId, boolean consent) {
        if (!consent) {
            throw new IllegalArgumentException("Explicit consent is required to accept the offer");
        }
        if (user.getNessieCheckingAccountId() == null) {
            throw new IllegalArgumentException("A linked checking account is required");
        }
        List<Pending> pending = new ArrayList<>();
        Loan loan = tx.execute(s -> {
            LoanOffer offer = offers.findByIdAndUserId(offerId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Offer not found"));
            if (offer.getStatus() != LoanOffer.Status.OFFERED) {
                throw new IllegalArgumentException("Offer is no longer available");
            }
            YearMonth current = clock.currentMonth();
            if (offer.getShortfallMonth().isBefore(current)) {
                throw new IllegalArgumentException("Offer has expired");
            }
            if (loans.findFirstByUserIdAndStatusIn(user.getId(), Loan.OPEN).isPresent()) {
                throw new IllegalArgumentException("You already have an open micro-loan");
            }
            offer.close(LoanOffer.Status.ACCEPTED);
            Loan created = new Loan(offer);
            pending.add(new Pending(Topics.LOAN_ACCEPTED, created, created.getDisburseMonth(), null));
            if (!created.getDisburseMonth().isAfter(current)) {
                created.startDisbursing();
                pending.add(new Pending(Topics.LOAN_DISBURSEMENT_REQUESTED, created, current, null));
            }
            return loans.save(created);
        });
        publish(pending);
        return loan;
    }

    public boolean decline(UUID userId, UUID offerId) {
        return Boolean.TRUE.equals(tx.execute(s -> offers.findByIdAndUserId(offerId, userId)
                .filter(o -> o.getStatus() == LoanOffer.Status.OFFERED)
                .map(o -> {
                    o.close(LoanOffer.Status.DECLINED);
                    return true;
                })
                .orElse(false)));
    }

    public Loan reschedule(UUID userId, UUID loanId) {
        return tx.execute(s -> {
            Loan loan = owned(userId, loanId);
            loan.reschedule(loan.getDisburseMonth().plusMonths(props.repaymentWindowMonths()));
            return loan;
        });
    }

    /** Repays now (the current simulated month) instead of the scheduled month; safeguards still apply. */
    public Loan repayEarly(UUID userId, UUID loanId) {
        List<Pending> pending = new ArrayList<>();
        Loan loan = tx.execute(s -> {
            Loan l = owned(userId, loanId);
            YearMonth current = clock.currentMonth();
            l.repayIn(current);
            l.startRepaying();
            pending.add(new Pending(Topics.LOAN_REPAYMENT_REQUESTED, l, current, "EARLY"));
            return l;
        });
        publish(pending);
        return loan;
    }

    /** End of {@code closing}: start due repayments, retry LATE ones within grace, default the rest. */
    public void closeMonth(YearMonth closing) {
        List<Pending> pending = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            for (Loan l : loans.findByStatusIn(List.of(Loan.Status.ACTIVE, Loan.Status.LATE))) {
                if (l.getStatus() == Loan.Status.LATE && closing.isAfter(l.getGraceUntilMonth())) {
                    l.defaulted();
                    pending.add(new Pending(Topics.LOAN_DEFAULTED, l, closing, l.getLastFailure()));
                } else if (l.getStatus() == Loan.Status.LATE || !l.getRepayMonth().isAfter(closing)) {
                    l.startRepaying();
                    pending.add(new Pending(Topics.LOAN_REPAYMENT_REQUESTED, l, closing, null));
                }
            }
        });
        publish(pending);
    }

    /** Start of {@code opened}: disburse loans whose shortfall month has arrived. */
    public void openMonth(YearMonth opened) {
        List<Pending> pending = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            for (Loan l : loans.findByStatusIn(List.of(Loan.Status.ACCEPTED))) {
                if (!l.getDisburseMonth().isAfter(opened)) {
                    l.startDisbursing();
                    pending.add(new Pending(Topics.LOAN_DISBURSEMENT_REQUESTED, l, opened, null));
                }
            }
        });
        publish(pending);
    }

    private Loan owned(UUID userId, UUID loanId) {
        return loans.findByIdAndUserId(loanId, userId).orElseThrow(() -> new IllegalArgumentException("Loan not found"));
    }

    private void publish(List<Pending> pending) {
        for (Pending p : pending) {
            events.publish(p.topic(), p.loan().getUserId(), event(p.loan(), p.month(), p.reason()));
        }
    }

    public static LoanEvent event(Loan l, YearMonth month, String reason) {
        return new LoanEvent(l.getId(), l.getStatus().name(), l.getPrincipal(), l.getFee(), month.toString(), reason);
    }
}
