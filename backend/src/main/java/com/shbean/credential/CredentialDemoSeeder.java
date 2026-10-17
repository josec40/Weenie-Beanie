package com.shbean.credential;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.shbean.events.EventPublisher;
import com.shbean.events.Topics;
import com.shbean.loan.LoanRepository;
import com.shbean.user.AppUserRepository;
import com.shbean.user.DemoProperties;

/** Dev demo: seeded repayment history so the demo user's first offer fits the Tier 1 cap. Best effort. */
@Component
@Order(4)
@ConditionalOnProperty(prefix = "app.demo", name = "seed-credential-history", havingValue = "true")
public class CredentialDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredentialDemoSeeder.class);

    private final CredentialService credentials;
    private final AppUserRepository users;
    private final LoanRepository loans;
    private final DemoProperties demo;
    private final EventPublisher events;

    public CredentialDemoSeeder(CredentialService credentials, AppUserRepository users, LoanRepository loans,
                                DemoProperties demo, EventPublisher events) {
        this.credentials = credentials;
        this.users = users;
        this.loans = loans;
        this.demo = demo;
        this.events = events;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.findByEmail(demo.email())
                .filter(u -> loans.findByUserIdOrderByCreatedAtDesc(u.getId()).isEmpty())
                .ifPresent(user -> {
                    try {
                        credentials.seedDemoHistory(user.getId()).ifPresent(change -> {
                            log.info("Seeded demo credential history: tier {} -> {}", change.previousTier(),
                                    change.current().tier());
                            events.publish(Topics.CREDENTIAL_UPDATED, user.getId(), change.event());
                        });
                    } catch (RuntimeException e) {
                        log.warn("Seeding demo credential history failed: {}", e.getMessage());
                    }
                });
    }
}
