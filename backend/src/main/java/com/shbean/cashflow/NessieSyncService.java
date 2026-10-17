package com.shbean.cashflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shbean.ai.MerchantCategorizer;
import com.shbean.cashflow.TransactionStore.TransactionRow;
import com.shbean.events.EventPublisher;
import com.shbean.events.Events.TransactionsIngested;
import com.shbean.events.Topics;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieException;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.Purchase;
import com.shbean.nessie.NessieModels.Transfer;
import com.shbean.user.AppUser;
import com.shbean.user.AppUserRepository;

/** Polls Nessie and stores transactions in TimescaleDB, deduplicated by Nessie _id. */
@Service
public class NessieSyncService {

    public record SyncResult(int fetched, int inserted) {
        static final SyncResult NONE = new SyncResult(0, 0);
    }

    static final String INCOME = "Income";
    static final String CASH = "Cash";
    static final String UNCATEGORIZED = "Uncategorized";

    private static final Logger log = LoggerFactory.getLogger(NessieSyncService.class);

    private final NessieClient nessie;
    private final TransactionStore store;
    private final AppUserRepository users;
    private final MerchantCategorizer categorizer;
    private final EventPublisher events;
    private final Map<String, String> merchantCategories = new ConcurrentHashMap<>();

    public NessieSyncService(NessieClient nessie, TransactionStore store, AppUserRepository users,
                             MerchantCategorizer categorizer, EventPublisher events) {
        this.nessie = nessie;
        this.store = store;
        this.users = users;
        this.categorizer = categorizer;
        this.events = events;
    }

    public void syncAll() {
        for (AppUser user : users.findByNessieCheckingAccountIdIsNotNull()) {
            try {
                sync(user);
            } catch (RuntimeException e) {
                log.warn("Nessie sync failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    public SyncResult sync(AppUser user) {
        String accountId = user.getNessieCheckingAccountId();
        if (accountId == null) {
            return SyncResult.NONE;
        }

        List<TransactionRow> rows = new ArrayList<>();
        for (Transfer d : nessie.listDeposits(accountId)) {
            if (!cancelled(d.status())) {
                rows.add(row(d.id(), d.transactionDate(), user, accountId, "DEPOSIT", d.amount().abs(), INCOME, d.description(), null, d.status()));
            }
        }
        for (Transfer w : nessie.listWithdrawals(accountId)) {
            if (!cancelled(w.status())) {
                rows.add(row(w.id(), w.transactionDate(), user, accountId, "WITHDRAWAL", w.amount().abs().negate(), CASH, w.description(), null, w.status()));
            }
        }
        for (Purchase p : nessie.listPurchases(accountId)) {
            if (!cancelled(p.status())) {
                rows.add(row(p.id(), p.purchaseDate(), user, accountId, "PURCHASE", p.amount().abs().negate(),
                        categoryOf(p.merchantId()), p.description(), p.merchantId(), p.status()));
            }
        }

        List<TransactionRow> inserted = store.insertNew(rows);
        BigDecimal balance = nextBalance(accountId, inserted);
        store.insertBalanceSnapshot(accountId, Instant.now(), user.getId(), balance);
        if (!inserted.isEmpty()) {
            refreshAggregate(inserted);
            LocalDate first = inserted.stream().map(r -> r.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate()).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate last = inserted.stream().map(r -> r.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate()).max(Comparator.naturalOrder()).orElseThrow();
            events.publish(Topics.TRANSACTIONS_INGESTED, user.getId(), new TransactionsIngested(inserted.size(), first, last, balance));
        }
        log.info("Nessie sync for user {}: fetched {}, inserted {}", user.getId(), rows.size(), inserted.size());
        return new SyncResult(rows.size(), inserted.size());
    }

    // Nessie never moves account balances, so the first sync anchors to Nessie's balance
    // and later syncs carry the last snapshot forward by whatever newly posted.
    private BigDecimal nextBalance(String accountId, List<TransactionRow> inserted) {
        return store.latestBalance(accountId)
                .map(last -> inserted.stream().map(TransactionRow::amount).reduce(last.balance(), BigDecimal::add))
                .orElseGet(() -> nessie.getAccount(accountId).balance());
    }

    private void refreshAggregate(List<TransactionRow> rows) {
        LocalDate first = rows.stream().map(TransactionRow::occurredAt).min(Comparator.naturalOrder())
                .map(i -> i.atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)).orElseThrow();
        LocalDate last = rows.stream().map(TransactionRow::occurredAt).max(Comparator.naturalOrder())
                .map(i -> i.atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)).orElseThrow();
        store.refreshMonthlyCashflow(first, last.plusMonths(1));
    }

    private String categoryOf(String merchantId) {
        if (merchantId == null) {
            return UNCATEGORIZED;
        }
        String cached = merchantCategories.get(merchantId);
        if (cached != null) {
            return cached;
        }
        try {
            Merchant m = nessie.getMerchant(merchantId);
            String nessieCategory = m == null || m.category() == null || m.category().isEmpty() ? null : m.category().getFirst();
            String category = categorizer.categorize(m == null ? null : m.name(), nessieCategory);
            if (!UNCATEGORIZED.equals(category)) {
                merchantCategories.put(merchantId, category);
            }
            return category;
        } catch (NessieException e) {
            return UNCATEGORIZED;
        }
    }

    private static TransactionRow row(String id, LocalDate date, AppUser user, String accountId, String kind,
                                      BigDecimal amount, String category, String description, String merchantId,
                                      String status) {
        Instant occurredAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TransactionRow(id, occurredAt, user.getId(), accountId, kind, amount, category,
                truncate(description), merchantId, status == null ? "unknown" : status.toLowerCase(Locale.ROOT));
    }

    private static boolean cancelled(String status) {
        return "cancelled".equalsIgnoreCase(status);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 255);
    }
}
