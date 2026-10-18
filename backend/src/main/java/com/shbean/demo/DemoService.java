package com.shbean.demo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Topics;
import com.shbean.loan.Loan;
import com.shbean.loan.LoanRepository;
import com.shbean.loan.LoanService;
import com.shbean.settlement.DisbursementSaga;
import com.shbean.settlement.RepaymentSaga;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;
import com.shbean.user.DemoProperties;

/**
 * advanceMonth: close the current simulated month (post the persona's transactions, sync, run due repayments),
 * move the clock, open the next month (due disbursements), then refresh every user's forecast.
 */
@Service
public class DemoService {

    public record ClockView(YearMonth currentMonth, int offset, boolean enabled) {
    }

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final DemoClock clock;
    private final DemoPoster poster;
    private final NessieSyncService sync;
    private final LoanService loans;
    private final LoanRepository loanRepo;
    private final TransactionStore store;
    private final ObjectProvider<RepaymentSaga> repayment;
    private final ObjectProvider<DisbursementSaga> disbursement;
    private final AppUserRepository users;
    private final DemoProperties demo;
    private final EventPublisher events;
    private final boolean enabled;

    public DemoService(DemoClock clock, DemoPoster poster, NessieSyncService sync, LoanService loans,
                       LoanRepository loanRepo, TransactionStore store,
                       ObjectProvider<RepaymentSaga> repayment, ObjectProvider<DisbursementSaga> disbursement,
                       AppUserRepository users, DemoProperties demo, EventPublisher events,
                       @Value("${app.demo.clock-enabled:true}") boolean enabled) {
        this.clock = clock;
        this.poster = poster;
        this.sync = sync;
        this.loans = loans;
        this.loanRepo = loanRepo;
        this.store = store;
        this.repayment = repayment;
        this.disbursement = disbursement;
        this.users = users;
        this.demo = demo;
        this.events = events;
        this.enabled = enabled;
    }

    public ClockView view() {
        return new ClockView(clock.currentMonth(), clock.offset(), enabled);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isDemoUser(AppUser user) {
        return user != null;
    }

    /** Demo tooling is enabled in dev environments for testing/simulation. */
    public void requireDemoUser(AppUser caller) {
        if (!enabled) {
            throw new IllegalArgumentException("The demo clock is disabled");
        }
    }

    public synchronized ClockView advanceMonths(AppUser caller, int count) {
        int toAdvance = Math.max(1, Math.min(count, 12));
        ClockView lastView = null;
        for (int i = 0; i < toAdvance; i++) {
            lastView = advanceMonth(caller);
        }
        return lastView != null ? lastView : view();
    }

    public synchronized ClockView advanceMonth(AppUser caller) {
        requireDemoUser(caller);
        YearMonth closing = clock.currentMonth();
        AppUser target = (caller != null && caller.getNessieCheckingAccountId() != null) ? caller : demoUser().orElse(caller);
        if (target != null && target.getNessieCheckingAccountId() != null) {
            try {
                poster.postAll(target.getNessieCheckingAccountId(),
                        DemoScenario.simulatedMonth(clock.scenarioAnchor(), closing, 42L), poster.merchantIds());
            } catch (Exception ex) {
                log.warn("Nessie transaction posting skipped/failed: {}", ex.getMessage());
            }
            try {
                sync.sync(target);
            } catch (Exception ex) {
                log.warn("Nessie sync skipped/failed: {}", ex.getMessage());
            }
        }

        // Refresh continuous aggregate so aggregate financial statements are realized in TimescaleDB
        try {
            store.refreshMonthlyCashflow(closing.minusMonths(1).atDay(1), closing.plusMonths(12).atDay(1));
        } catch (Exception ex) {
            log.warn("Continuous aggregate refresh skipped: {}", ex.getMessage());
        }

        loans.closeMonth(closing);

        // Immediate fulfillment of due repayments for zero-lag UI updates
        repayment.ifAvailable(saga -> {
            for (Loan l : loanRepo.findByStatusIn(List.of(Loan.Status.REPAYING))) {
                try {
                    saga.run(l.getId(), closing, null);
                } catch (Exception ex) {
                    log.warn("Immediate repayment for loan {} failed: {}", l.getId(), ex.getMessage());
                }
            }
        });

        YearMonth opened = clock.advance();
        loans.openMonth(opened);

        // Immediate fulfillment of due disbursements
        disbursement.ifAvailable(saga -> {
            for (Loan l : loanRepo.findByStatusIn(List.of(Loan.Status.DISBURSING))) {
                try {
                    saga.run(l.getId(), opened, null);
                } catch (Exception ex) {
                    log.warn("Immediate disbursement for loan {} failed: {}", l.getId(), ex.getMessage());
                }
            }
        });

        for (AppUser u : users.findByNessieCheckingAccountIdIsNotNull()) {
            events.publish(Topics.CASHFLOW_CHANGED, u.getId(), new CashflowChanged(CashflowReason.CLOCK));
        }
        log.info("Demo clock advanced: {} closed, {} opened", closing, opened);
        return view();
    }

    private Optional<AppUser> demoUser() {
        if (demo.email() == null || demo.email().isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(demo.email().toLowerCase(Locale.ROOT)).filter(u -> u.getNessieCheckingAccountId() != null);
    }
}
