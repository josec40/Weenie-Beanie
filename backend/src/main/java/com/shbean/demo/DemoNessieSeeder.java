package com.shbean.demo;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.demo.DemoScenario.MerchantSpec;
import com.shbean.demo.DemoScenario.PlannedTxn;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Topics;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Address;
import com.shbean.nessie.NessieModels.CreateAccountRequest;
import com.shbean.nessie.NessieModels.CreateCustomerRequest;
import com.shbean.nessie.NessieModels.CreateMerchantRequest;
import com.shbean.nessie.NessieModels.Customer;
import com.shbean.nessie.NessieModels.Geocode;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;
import com.shbean.user.DemoProperties;

/** Creates the "Alex" scenario in Nessie for the demo user (once), then runs an initial sync. */
@Component
@Profile("dev")
@Order(2)
public class DemoNessieSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoNessieSeeder.class);
    private static final Address ADDRESS = new Address("100", "Bay St", "Tampa", "FL", "33620");
    private static final Geocode GEOCODE = new Geocode(27.95, -82.45);

    private final NessieClient nessie;
    private final AppUserRepository users;
    private final NessieSyncService sync;
    private final DemoProperties demo;
    private final EventPublisher events;
    private final DemoPoster poster;
    private final DemoClock clock;

    public DemoNessieSeeder(NessieClient nessie, AppUserRepository users, NessieSyncService sync, DemoProperties demo,
                            EventPublisher events, DemoPoster poster, DemoClock clock) {
        this.nessie = nessie;
        this.users = users;
        this.sync = sync;
        this.demo = demo;
        this.events = events;
        this.poster = poster;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!demo.seedNessie() || demo.email() == null || demo.email().isBlank()) {
            return;
        }
        AppUser user = users.findByEmail(demo.email().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) {
            return;
        }
        try {
            if (user.getNessieCheckingAccountId() == null) {
                seed(user);
            }
            if (sync.sync(user).inserted() == 0) {
                // Nothing new to ingest, but still refresh the forecast snapshot (the month may have rolled over).
                events.publish(Topics.CASHFLOW_CHANGED, user.getId(), new CashflowChanged(CashflowReason.TRANSACTIONS));
            }
        } catch (RuntimeException e) {
            log.warn("Demo Nessie seeding/sync failed: {}", e.getMessage());
        }
    }

    private void seed(AppUser user) {
        log.info("Seeding Nessie demo scenario for {}", user.getEmail());
        Customer customer = nessie.createCustomer(new CreateCustomerRequest("Alex", "Rivera", ADDRESS));
        Account account = nessie.createAccount(customer.id(),
                new CreateAccountRequest("Checking", "Alex Checking", 0, DemoScenario.CURRENT_BALANCE));

        Map<String, String> merchantIds = new HashMap<>();
        for (MerchantSpec m : DemoScenario.MERCHANTS) {
            merchantIds.put(m.key(), nessie.createMerchant(new CreateMerchantRequest(m.name(), m.category(), ADDRESS, GEOCODE)).id());
        }

        YearMonth anchor = clock.currentMonth();
        List<PlannedTxn> history = DemoScenario.history(anchor, 42L);
        poster.postAll(account.id(), history, merchantIds);
        clock.setScenarioAnchor(anchor);

        // Link only after the full history is posted so a partial seed is retried on next start.
        user.linkNessie(customer.id(), account.id());
        users.save(user);
        log.info("Seeded {} Nessie transactions for {}", history.size(), user.getEmail());
    }
}
