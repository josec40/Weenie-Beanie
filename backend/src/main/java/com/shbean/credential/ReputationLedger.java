package com.shbean.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shbean.events.Events.RepaymentOutcome;
import com.shbean.settlement.OnChainExecutor;
import com.shbean.settlement.OnChainExecutor.Plan;
import com.shbean.settlement.SettlementException;
import com.shbean.settlement.SolanaBank;
import com.shbean.solana.AnchorCodec;
import com.shbean.solana.AnchorCodec.ReputationAccount;
import com.shbean.solana.ReputationProgram;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaClient.Instruction;
import com.shbean.solana.SolanaClient.TxResult;
import com.shbean.solana.SolanaProperties;
import com.shbean.wallet.WalletService;

/** On-chain reputation writes (bank authority only) and reads; the PDA is the source of truth for tiers. */
@Component
@ConditionalOnProperty(prefix = "solana", name = "enabled", havingValue = "true")
public class ReputationLedger {

    static final String SBT_MINT = "SBT_MINT";
    private static final int MINT_COMPUTE_UNITS = 400_000;
    private static final int MAX_URI_LENGTH = 200;
    private static final Logger log = LoggerFactory.getLogger(ReputationLedger.class);

    private final SolanaClient client;
    private final SolanaBank bank;
    private final SolanaProperties props;
    private final WalletService wallets;
    private final OnChainExecutor executor;
    private final ReputationProgram program;
    private final String publicBaseUrl;

    public ReputationLedger(SolanaClient client, SolanaBank bank, SolanaProperties props, WalletService wallets,
                            OnChainExecutor executor, @Value("${app.public-base-url}") String publicBaseUrl) {
        this.client = client;
        this.bank = bank;
        this.props = props;
        this.wallets = wallets;
        this.executor = executor;
        this.program = new ReputationProgram(client, props.reputationProgramId(), bank.authority().address());
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public ReputationProgram program() {
        return program;
    }

    static String recordStep(RepaymentOutcome outcome) {
        return "REP_" + outcome.name();
    }

    public String verifyUrl(String wallet) {
        return publicBaseUrl + "/api/public/credentials/" + wallet;
    }

    public synchronized void ensureConfig() {
        if (client.getAccount(program.config()).isEmpty()) {
            confirm("init_config", sendNow(List.of(program.initConfig())));
            log.info("Initialized reputation config {}", program.config());
        }
    }

    /** The user's custodial wallet (created on first use) with its reputation PDA initialized. */
    public String walletWithReputation(UUID userId) {
        String wallet = wallets.keypairFor(userId).address();
        if (read(wallet).isEmpty()) {
            ensureConfig();
            confirm("init_reputation", sendNow(List.of(program.initReputation(wallet))));
        }
        return wallet;
    }

    /** Exactly once per loan: the counters cannot show whether this loan was applied, the step log can. */
    public String record(UUID loanId, String wallet, RepaymentOutcome outcome, long amountUnits) {
        Instruction ix = outcome == RepaymentOutcome.DEFAULT
                ? program.recordDefault(wallet)
                : program.recordRepayment(wallet, outcome == RepaymentOutcome.ON_TIME, amountUnits);
        return executor.execute(loanId, recordStep(outcome), () -> false, () -> bankPlan(List.of(ix)));
    }

    /** Mints the soulbound credential once the wallet has an on-time repayment; empty if not (yet) eligible. */
    public Optional<String> mintIfEligible(UUID loanId, String wallet) {
        ReputationAccount rep = read(wallet).orElseThrow();
        if (rep.hasCredential() || rep.onTimeRepayments() < 1) {
            return Optional.empty();
        }
        String uri = verifyUrl(wallet);
        if (uri.length() > MAX_URI_LENGTH) {
            throw new IllegalStateException("app.public-base-url is too long for the credential metadata URI");
        }
        return Optional.ofNullable(executor.execute(loanId, SBT_MINT,
                () -> read(wallet).map(ReputationAccount::hasCredential).orElse(false),
                () -> bankPlan(List.of(ReputationProgram.computeUnitLimit(MINT_COMPUTE_UNITS),
                        program.mintCredential(wallet, uri)))));
    }

    /** Dev-only seeded history: plain on-time repayments with no loan behind them. */
    public void seedOnTime(String wallet, int count, long amountUnits) {
        for (int i = 0; i < count; i++) {
            confirm("record_repayment", sendNow(List.of(program.recordRepayment(wallet, true, amountUnits))));
        }
    }

    public Optional<ReputationAccount> read(String wallet) {
        return client.getAccount(program.reputationAddress(wallet)).map(a -> AnchorCodec.decodeReputation(a.data()));
    }

    private TxResult sendNow(List<Instruction> instructions) {
        return client.sendAndConfirm(instructions, bank.authority(), List.of(), props.confirmTimeout());
    }

    private Plan bankPlan(List<Instruction> instructions) {
        return new Plan(instructions, bank.authority(), List.of());
    }

    private static void confirm(String what, TxResult result) {
        if (!result.succeeded()) {
            throw new SettlementException(what + " did not confirm: " + result.status() + " " + result.error());
        }
    }
}
