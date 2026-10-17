package com.shbean.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.cashflow.RecurringExpense;
import com.shbean.cashflow.RecurringExpenseService;
import com.shbean.cashflow.TransactionStore;
import com.shbean.credential.CredentialService;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.CashflowChanged;
import com.shbean.events.Events.CashflowReason;
import com.shbean.events.Topics;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/**
 * Dev-only "Reset demo": wipes the demo user's local state, gives them a fresh Nessie customer and custodial wallet,
 * re-seeds Tier 1 history, and rewinds the (global) demo clock. Old devnet accounts and Nessie customers stay behind.
 */
@Service
public class DemoResetService implements DisposableBean {

    public enum State { IDLE, RUNNING, DONE, FAILED }

    public record ResetStatus(State state, String message, Instant updatedAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppUserRepository users;
    private final RecurringExpenseService expenses;
    private final TransactionStore store;
    private final DemoNessieSeeder seeder;
    private final CredentialService credentials;
    private final EventPublisher events;
    private final AtomicReference<ResetStatus> status = new AtomicReference<>(new ResetStatus(State.IDLE, null, null));
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "demo-reset"));

    public DemoResetService(JdbcTemplate jdbc, TransactionTemplate tx, AppUserRepository users,
                            RecurringExpenseService expenses, TransactionStore store, DemoNessieSeeder seeder,
                            CredentialService credentials, EventPublisher events) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.users = users;
        this.expenses = expenses;
        this.store = store;
        this.seeder = seeder;
        this.credentials = credentials;
        this.events = events;
    }

    public ResetStatus status() {
        return status.get();
    }

    /** Starts the reset in the background; a second call while one is running returns the running status. */
    public ResetStatus start(AppUser demoUser) {
        ResetStatus current = status.get();
        if (current.state() == State.RUNNING) {
            return current;
        }
        ResetStatus running = new ResetStatus(State.RUNNING, "Clearing demo data…", Instant.now());
        if (!status.compareAndSet(current, running)) {
            return status.get();
        }
        UUID userId = demoUser.getId();
        worker.submit(() -> run(userId));
        return running;
    }

    private void run(UUID userId) {
        try {
            for (RecurringExpense e : expenses.list(userId)) {
                expenses.delete(userId, e.getId());
            }
            tx.executeWithoutResult(s -> clear(userId));
            store.refreshMonthlyCashflow(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));

            step("Seeding bank history in Nessie…");
            seeder.seedAndSync(users.findById(userId).orElseThrow());

            step("Restoring repayment history…");
            credentials.seedDemoHistory(userId)
                    .ifPresent(change -> events.publish(Topics.CREDENTIAL_UPDATED, userId, change.event()));
            events.publish(Topics.CASHFLOW_CHANGED, userId, new CashflowChanged(CashflowReason.CLOCK));
            status.set(new ResetStatus(State.DONE, "Demo reset complete", Instant.now()));
            log.info("Demo reset complete for user {}", userId);
        } catch (RuntimeException e) {
            log.warn("Demo reset failed: {}", e.getMessage());
            status.set(new ResetStatus(State.FAILED, "Reset failed: " + e.getMessage(), Instant.now()));
        }
    }

    private void clear(UUID userId) {
        jdbc.update("DELETE FROM notification WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM settlement_step WHERE loan_id IN (SELECT id FROM loan WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM loan WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM loan_offer WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM forecast_snapshot WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credential WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM wallet WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_transaction WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM balance_snapshot WHERE user_id = ?", userId);
        jdbc.update("UPDATE app_user SET nessie_customer_id = NULL, nessie_checking_account_id = NULL WHERE id = ?", userId);
        jdbc.update("UPDATE demo_clock SET month_offset = 0, updated_at = now() WHERE id = 1");
    }

    private void step(String message) {
        status.set(new ResetStatus(State.RUNNING, message, Instant.now()));
    }

    @Override
    public void destroy() {
        worker.shutdownNow();
    }
}
