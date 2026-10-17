package com.shbean.credential;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.shbean.credential.CredentialStore.Credential;
import com.shbean.events.Events.CredentialUpdated;
import com.shbean.events.Events.RepaymentOutcome;
import com.shbean.settlement.SettlementSteps;
import com.shbean.settlement.SolanaBank;
import com.shbean.solana.AnchorCodec.ReputationAccount;
import com.shbean.solana.Base58;

/**
 * Applies loan outcomes to the user's reputation exactly once per loan. With Solana enabled the on-chain PDA is
 * written first and then mirrored into {@code credential}; in fiat-only mode the row is the record itself.
 */
@Service
public class CredentialService {

    static final String APPLIED = "CREDENTIAL";
    static final BigDecimal SEEDED_REPAYMENT = new BigDecimal("150.00");
    static final int SEEDED_ON_TIME = 2;

    public record Change(UUID userId, Credential before, Credential current) {

        public int previousTier() {
            return before.tier();
        }

        public CredentialUpdated event() {
            return new CredentialUpdated(current.tier(), before.tier(), current.onTimeRepayments(),
                    before.onTimeRepayments(), current.lateRepayments(), current.defaults(), current.sbtMint() != null);
        }
    }

    private final CredentialStore store;
    private final ObjectProvider<ReputationLedger> chain;
    private final SettlementSteps steps;
    private final TransactionTemplate tx;

    public CredentialService(CredentialStore store, ObjectProvider<ReputationLedger> chain, SettlementSteps steps,
                             TransactionTemplate tx) {
        this.store = store;
        this.chain = chain;
        this.steps = steps;
        this.tx = tx;
    }

    public Credential current(UUID userId) {
        return store.find(userId).orElse(Credential.empty(userId));
    }

    /** Empty if this loan's outcome was already applied. */
    public Optional<Change> apply(UUID userId, UUID loanId, RepaymentOutcome outcome, BigDecimal amount) {
        if (steps.find(loanId, APPLIED).filter(s -> s.state() == SettlementSteps.State.DONE).isPresent()) {
            return Optional.empty();
        }
        Credential before = current(userId);
        ReputationLedger ledger = chain.getIfAvailable();
        Credential after;
        if (ledger != null) {
            String wallet = ledger.walletWithReputation(userId);
            ledger.record(loanId, wallet, outcome, SolanaBank.units(amount));
            String mintSig = ledger.mintIfEligible(loanId, wallet).orElse(before.sbtMintSignature());
            after = mirror(ledger, userId, wallet, mintSig);
        } else {
            after = locally(before, outcome, amount);
        }
        tx.executeWithoutResult(s -> {
            store.save(after);
            steps.done(loanId, APPLIED, null, null);
        });
        return Optional.of(new Change(userId, before, after));
    }

    /** Dev demo: gives a user without loans two prior on-time repayments (Tier 1) without minting the credential. */
    public Optional<Change> seedDemoHistory(UUID userId) {
        Credential before = current(userId);
        ReputationLedger ledger = chain.getIfAvailable();
        Credential after;
        if (ledger != null) {
            String wallet = ledger.walletWithReputation(userId);
            ReputationAccount rep = ledger.read(wallet).orElseThrow();
            if (rep.onTimeRepayments() < SEEDED_ON_TIME && rep.defaults() == 0) {
                ledger.seedOnTime(wallet, SEEDED_ON_TIME - rep.onTimeRepayments(), SolanaBank.units(SEEDED_REPAYMENT));
            }
            after = mirror(ledger, userId, wallet, before.sbtMintSignature());
        } else if (before.onTimeRepayments() < SEEDED_ON_TIME && before.defaults() == 0) {
            int missing = SEEDED_ON_TIME - before.onTimeRepayments();
            after = new Credential(userId, null, null, Tiers.tierFor(SEEDED_ON_TIME, 0), SEEDED_ON_TIME,
                    before.lateRepayments(), 0,
                    before.totalRepaid().add(SEEDED_REPAYMENT.multiply(BigDecimal.valueOf(missing))), null, null,
                    Instant.now());
        } else {
            after = before;
        }
        store.save(after);
        boolean changed = after.onTimeRepayments() != before.onTimeRepayments() || after.tier() != before.tier()
                || !Objects.equals(after.wallet(), before.wallet());
        return changed ? Optional.of(new Change(userId, before, after)) : Optional.empty();
    }

    private static Credential mirror(ReputationLedger ledger, UUID userId, String wallet, String mintSig) {
        ReputationAccount rep = ledger.read(wallet).orElseThrow();
        return new Credential(userId, wallet, ledger.program().reputationAddress(wallet), rep.tier(),
                rep.onTimeRepayments(), rep.lateRepayments(), rep.defaults(),
                BigDecimal.valueOf(rep.totalRepaid()).movePointLeft(SolanaBank.DECIMALS).setScale(2, RoundingMode.DOWN),
                rep.hasCredential() ? Base58.encode(rep.sbtMint()) : null, rep.hasCredential() ? mintSig : null,
                Instant.ofEpochSecond(rep.updatedAt()));
    }

    private static Credential locally(Credential c, RepaymentOutcome outcome, BigDecimal amount) {
        int onTime = c.onTimeRepayments() + (outcome == RepaymentOutcome.ON_TIME ? 1 : 0);
        int late = c.lateRepayments() + (outcome == RepaymentOutcome.LATE ? 1 : 0);
        int defaults = c.defaults() + (outcome == RepaymentOutcome.DEFAULT ? 1 : 0);
        BigDecimal repaid = outcome == RepaymentOutcome.DEFAULT ? c.totalRepaid() : c.totalRepaid().add(amount);
        return new Credential(c.userId(), c.wallet(), c.reputationAddress(), Tiers.tierFor(onTime, defaults), onTime,
                late, defaults, repaid, c.sbtMint(), c.sbtMintSignature(), Instant.now());
    }
}
