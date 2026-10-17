package com.shbean.demo;

import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Topics;
import com.shbean.loan.LoanService;
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
    private final AppUserRepository users;
    private final DemoProperties demo;
    private final EventPublisher events;
    private final boolean enabled;

    public DemoService(DemoClock clock, DemoPoster poster, NessieSyncService sync, LoanService loans,
                       AppUserRepository users, DemoProperties demo, EventPublisher events,
                       @Value("${app.demo.clock-enabled:false}") boolean enabled) {
        this.clock = clock;
        this.poster = poster;
        this.sync = sync;
        this.loans = loans;
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
        return user != null && demo.email() != null && user.getEmail().equalsIgnoreCase(demo.email().strip());
    }

    /** Demo tooling is dev-only and limited to the demo persona (the clock is global). */
    public void requireDemoUser(AppUser caller) {
        if (!enabled) {
            throw new IllegalArgumentException("The demo clock is disabled");
        }
        if (!isDemoUser(caller)) {
            throw new IllegalArgumentException("Demo controls are only available to the demo account");
        }
    }

    public synchronized ClockView advanceMonth(AppUser caller) {
        requireDemoUser(caller);
        YearMonth closing = clock.currentMonth();
        demoUser().ifPresent(u -> {
            poster.postAll(u.getNessieCheckingAccountId(),
                    DemoScenario.simulatedMonth(clock.scenarioAnchor(), closing, 42L), poster.merchantIds());
            sync.sync(u);
        });
        loans.closeMonth(closing);
        YearMonth opened = clock.advance();
        loans.openMonth(opened);
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
