package com.shbean.settlement;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shbean.solana.Keypair;
import com.shbean.solana.MicroloanProgram;
import com.shbean.solana.SolanaClient;
import com.shbean.solana.SolanaClient.TxResult;
import com.shbean.solana.SolanaProperties;
import com.shbean.solana.SplToken;

/** The bank authority: pays fees and rent, owns the mock USDC mint and the loan pool. */
@Component
@ConditionalOnProperty(prefix = "solana", name = "enabled", havingValue = "true")
public class SolanaBank {

    /** Mock USDC decimals (decision 5). */
    public static final int DECIMALS = 6;

    private static final Logger log = LoggerFactory.getLogger(SolanaBank.class);

    private final SolanaClient client;
    private final SolanaProperties props;
    private final Keypair authority;
    private final MicroloanProgram microloan;

    public SolanaBank(SolanaClient client, SolanaProperties props) {
        this.client = client;
        this.props = props;
        this.authority = loadKeypair(props.bankKeypairPath());
        this.microloan = new MicroloanProgram(client, props.microloanProgramId(), props.usdcMint());
    }

    public Keypair authority() {
        return authority;
    }

    public MicroloanProgram microloan() {
        return microloan;
    }

    public String vendorWalletAddress() {
        return props.vendorWalletAddress() != null && !props.vendorWalletAddress().isBlank()
                ? props.vendorWalletAddress()
                : authority.address();
    }

    public static long units(BigDecimal usd) {
        return usd.movePointRight(DECIMALS).longValueExact();
    }

    public long tokenBalance(String tokenAccount) {
        return client.getAccount(tokenAccount).map(a -> SplToken.amount(a.data())).orElse(0L);
    }

    /** Initializes the pool if needed and tops up the vault (mint mock USDC → fund_pool) until it holds {@code needed}. */
    public synchronized void ensurePool(long needed) {
        if (client.getAccount(microloan.pool()).isEmpty()) {
            confirm("init_pool", client.sendAndConfirm(List.of(microloan.initPool(authority.address())), authority,
                    List.of(), props.confirmTimeout()));
            log.info("Initialized loan pool {} (vault {})", microloan.pool(), microloan.vault());
        }
        long vault = tokenBalance(microloan.vault());
        if (vault >= needed) {
            return;
        }
        long amount = Math.max(units(BigDecimal.valueOf(props.poolTopUpUsdc())), needed - vault);
        String bank = authority.address();
        String bankToken = SplToken.associatedTokenAddress(client, bank, microloan.mint());
        // Minting extra mock USDC is harmless, so this top-up does not need the saga's exactly-once machinery.
        confirm("fund_pool", client.sendAndConfirm(List.of(
                SplToken.createAssociatedTokenAccountIdempotent(bank, bankToken, bank, microloan.mint()),
                SplToken.mintTo(microloan.mint(), bankToken, bank, amount),
                microloan.fundPool(bank, bankToken, amount)), authority, List.of(), props.confirmTimeout()));
        log.info("Funded loan pool with {} mock USDC units", amount);
    }

    private static void confirm(String what, TxResult result) {
        if (!result.succeeded()) {
            throw new SettlementException(what + " did not confirm: " + result.status() + " " + result.error());
        }
    }

    private static Keypair loadKeypair(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("solana.bank-keypair-path is required when solana.enabled=true");
        }
        try {
            return Keypair.fromJson(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the bank keypair file " + path);
        }
    }
}
