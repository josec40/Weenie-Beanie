package com.shbean.settlement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.shbean.settlement.SettlementSteps.State;
import com.shbean.settlement.SettlementSteps.Step;
import com.shbean.solana.Keypair;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SolanaClient.SignedTransaction;
import com.shbean.solana.SolanaClient.Status;
import com.shbean.solana.SolanaClient.TxResult;
import com.shbean.solana.SolanaException;
import com.shbean.solana.SolanaProperties;

/**
 * Runs one on-chain saga step at most once. The signature and blockhash are recorded before submission; a resumed
 * step first resolves that signature and only builds a new transaction once the old one has provably not landed
 * (failed on-chain, or unseen after its blockhash expired). {@code alreadyDone} checks program state as a second guard.
 */
@Component
public class OnChainExecutor {

    /** What to send; built lazily so nothing is signed when the step turns out to be done already. */
    public record Plan(List<Instruction> instructions, Keypair feePayer, List<Keypair> otherSigners) {
    }

    private final SolanaClient client;
    private final SettlementSteps steps;
    private final Duration confirmTimeout;
    private final Duration pendingTimeout;
    private final Duration pollInterval;

    @Autowired
    public OnChainExecutor(SolanaClient client, SettlementSteps steps, SolanaProperties props) {
        // A blockhash is valid for ~150 slots (60–90 s); wait a bit longer than that for an unseen transaction.
        this(client, steps, props.confirmTimeout(), Duration.ofSeconds(150), Duration.ofSeconds(2));
    }

    OnChainExecutor(SolanaClient client, SettlementSteps steps, Duration confirmTimeout, Duration pendingTimeout,
                    Duration pollInterval) {
        this.client = client;
        this.steps = steps;
        this.confirmTimeout = confirmTimeout;
        this.pendingTimeout = pendingTimeout;
        this.pollInterval = pollInterval;
    }

    /**
     * @return the signature of the transaction that performed the step, or {@code null} if the chain already showed
     * the effect and no signature of ours is known
     */
    public String execute(UUID loanId, String step, BooleanSupplier alreadyDone, Supplier<Plan> plan) {
        Optional<Step> row = steps.find(loanId, step);
        if (row.isPresent() && row.get().state() == State.DONE) {
            return row.get().signature();
        }
        Optional<String> landed = resolveRecorded(loanId, step, row);
        if (landed.isPresent()) {
            return landed.get();
        }
        if (alreadyDone.getAsBoolean()) {
            steps.done(loanId, step, null, null);
            return null;
        }

        Plan p = plan.get();
        SignedTransaction tx = client.sign(p.instructions(), p.feePayer(), p.otherSigners());
        steps.pending(loanId, step, tx.signature(), tx.blockhash());
        try {
            client.submit(tx);
        } catch (SolanaException e) {
            // Usually a preflight rejection, but the node may have forwarded it anyway: keep PENDING and let the
            // next attempt resolve the signature before trying again.
            throw new SettlementException(step + " was not accepted: " + e.getMessage(), e);
        }
        TxResult result = client.awaitConfirmation(tx.signature(), confirmTimeout);
        if (result.succeeded()) {
            steps.done(loanId, step, tx.signature(), null);
            return tx.signature();
        }
        if (result.status() == Status.FAILED) {
            steps.failed(loanId, step, result.error());
            throw new SettlementException(step + " failed on-chain: " + result.error());
        }
        throw new SettlementException(step + " not confirmed within " + confirmTimeout + ": " + tx.signature());
    }

    /** Lets an in-flight transaction for {@code step} settle (landed → DONE, otherwise FAILED) before a decision. */
    public void awaitPending(UUID loanId, String step) {
        resolveRecorded(loanId, step, steps.find(loanId, step));
    }

    private Optional<String> resolveRecorded(UUID loanId, String step, Optional<Step> row) {
        if (row.isEmpty() || row.get().state() != State.PENDING || row.get().signature() == null) {
            return Optional.empty();
        }
        String signature = row.get().signature();
        TxResult result = resolve(step, signature, row.get().blockhash());
        if (result.succeeded()) {
            steps.done(loanId, step, signature, null);
            return Optional.of(signature);
        }
        // Failed on-chain or expired unseen: it had no effect, so a fresh transaction is safe.
        steps.failed(loanId, step, result.status() == Status.FAILED ? result.error() : "expired before landing");
        return Optional.empty();
    }

    private TxResult resolve(String step, String signature, String blockhash) {
        long deadline = System.nanoTime() + pendingTimeout.toNanos();
        while (true) {
            TxResult result = client.status(signature);
            if (result.succeeded() || result.status() == Status.FAILED) {
                return result;
            }
            if (result.status() == Status.NOT_FOUND && !client.isBlockhashValid(blockhash)) {
                // Re-check once: it may have landed just before the blockhash expired.
                result = client.status(signature);
                if (result.status() == Status.NOT_FOUND || result.succeeded() || result.status() == Status.FAILED) {
                    return result;
                }
            }
            if (System.nanoTime() > deadline) {
                throw new SettlementException(step + " is still pending: " + signature);
            }
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SettlementException("Interrupted while resolving a pending transaction");
        }
    }
}
