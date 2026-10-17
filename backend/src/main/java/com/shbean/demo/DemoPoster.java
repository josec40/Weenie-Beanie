package com.shbean.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

import com.shbean.demo.DemoScenario.MerchantSpec;
import com.shbean.demo.DemoScenario.PlannedTxn;
import com.shbean.nessie.NessieClient;
import com.shbean.nessie.NessieModels.Merchant;
import com.shbean.nessie.NessieModels.CreatePurchaseRequest;
import com.shbean.nessie.NessieModels.CreateTransferRequest;

/** Posts demo-persona transactions to Nessie (8-way parallel). */
@Component
public class DemoPoster {

    private static final int PARALLELISM = 8;

    private final NessieClient nessie;
    private volatile Map<String, String> merchantIdsByKey;

    public DemoPoster(NessieClient nessie) {
        this.nessie = nessie;
    }

    public void postAll(String accountId, List<PlannedTxn> txns, Map<String, String> merchantIds) {
        List<Callable<Object>> posts = txns.stream().<Callable<Object>>map(t -> () -> post(accountId, t, merchantIds)).toList();
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLELISM)) {
            for (Future<Object> f : pool.invokeAll(posts)) {
                f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while posting to Nessie");
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause().getMessage());
        }
    }

    /** Demo merchant ids looked up by name in Nessie (merchants are created once by the seeder). */
    public Map<String, String> merchantIds() {
        if (merchantIdsByKey == null) {
            Map<String, String> byName = new HashMap<>();
            for (Merchant m : nessie.listMerchants()) {
                byName.putIfAbsent(m.name(), m.id());
            }
            Map<String, String> byKey = new HashMap<>();
            for (MerchantSpec spec : DemoScenario.MERCHANTS) {
                String id = byName.get(spec.name());
                if (id == null) {
                    throw new IllegalStateException("Demo merchant missing in Nessie: " + spec.name());
                }
                byKey.put(spec.key(), id);
            }
            merchantIdsByKey = Map.copyOf(byKey);
        }
        return merchantIdsByKey;
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
