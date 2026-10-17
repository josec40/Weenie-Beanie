package com.shbean.demo;

import com.shbean.cashflow.NessieSyncService;
import com.shbean.demo.DemoScenario.MerchantSpec;
import com.shbean.demo.DemoScenario.PlannedTxn;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Account;
import com.shbean.nessie.NessieModels.Address;
import com.shbean.nessie.NessieModels.CreateAccountRequest;
import com.shbean.nessie.NessieModels.CreateCustomerRequest;
import com.shbean.nessie.NessieModels.CreateMerchantRequest;
import com.shbean.nessie.NessieModels.CreatePurchaseRequest;
import com.shbean.nessie.NessieModels.CreateTransferRequest;
import com.shbean.nessie.NessieModels.Customer;
import com.shbean.nessie.NessieModels.Geocode;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;
import com.shbean.user.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Creates the "Alex" scenario in Nessie for the demo user (once), then runs an initial sync. */
@Component
@Profile("dev")
@Order(2)
public class DemoNessieSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoNessieSeeder.class);
    private static final Address ADDRESS = new Address("100", "Bay St", "Tampa", "FL", "33620");
    private static final Geocode GEOCODE = new Geocode(27.95, -82.45);
    private static final int PARALLELISM = 8;

    private final NessieClient nessie;
    private final AppUserRepository users;
    private final NessieSyncService sync;
    private final DemoProperties demo;

    public DemoNessieSeeder(NessieClient nessie, AppUserRepository users, NessieSyncService sync, DemoProperties demo) {
        this.nessie = nessie;
        this.users = users;
        this.sync = sync;
        this.demo = demo;
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
            sync.sync(user);
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

        List<PlannedTxn> history = DemoScenario.history(YearMonth.now(ZoneOffset.UTC), 42L);
        List<Callable<Object>> posts = history.stream().<Callable<Object>>map(t -> () -> post(account.id(), t, merchantIds)).toList();
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM)) {
            for (Future<Object> f : pool.invokeAll(posts)) {
                f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while seeding Nessie");
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage());
        }

        // Link only after the full history is posted so a partial seed is retried on next start.
        user.linkNessie(customer.id(), account.id());
        users.save(user);
        log.info("Seeded {} Nessie transactions for {}", history.size(), user.getEmail());
    }

    private Object post(String accountId, PlannedTxn t, Map<String, String> merchantIds) {
        return switch (t.kind()) {
            case DEPOSIT -> nessie.createDeposit(accountId,
                    new CreateTransferRequest("balance", t.date(), "completed", t.amount(), t.description()));
            case WITHDRAWAL -> nessie.createWithdrawal(accountId,
                    new CreateTransferRequest("balance", t.date(), "completed", t.amount(), t.description()));
            case PURCHASE -> nessie.createPurchase(accountId,
                    new CreatePurchaseRequest(merchantIds.get(t.merchantKey()), "balance", t.date(), t.amount(), "completed", t.description()));
        };
    }
}
